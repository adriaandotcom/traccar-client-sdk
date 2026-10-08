package org.traccar.client

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.plus
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.CoreLocation.kCLDistanceFilterNone
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.CoreLocation.kCLLocationAccuracyBestForNavigation
import platform.CoreLocation.kCLLocationAccuracyHundredMeters
import platform.CoreLocation.kCLLocationAccuracyKilometer
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.timeIntervalSince1970
import platform.darwin.NSObject

class IosLocationSource(
    scope: ComponentCoroutineScope,
    config: Config,
    private val state: StateFlow<State>,
) : LocationSource, SignalSource {
    override val signals = MutableSharedFlow<Signal>(extraBufferCapacity = 8)

    private val locationConfig = config.location.effective
    private val mainScope = scope + Dispatchers.Main

    override val positions = MutableSharedFlow<Position>(extraBufferCapacity = 8)

    private var manager: CLLocationManager? = null
    private var delegate: CLLocationManagerDelegateProtocol? = null
    private var lastLocation: CLLocation? = null
    private var pendingLocation: CompletableDeferred<CLLocation?>? = null

    init {
        mainScope.observeState(state, State::locationMode, inactive = LocationMode.Off) { mode ->
            when (mode) {
                LocationMode.Active -> ensureStarted()
                LocationMode.Stationary -> {
                    if (manager == null) ensureStarted(standard = false)
                    ensureStopped(awaitFinalFix = true)
                }
                LocationMode.Off -> ensureStopped(awaitFinalFix = false)
            }
        }
    }

    private suspend fun ensureStarted(standard: Boolean = true) {
        manager?.let { if (standard) it.startUpdatingLocation(); return }

        val newDelegate = object : NSObject(), CLLocationManagerDelegateProtocol {
            override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
                didUpdateLocations.forEach { value ->
                    val location = value as CLLocation
                    lastLocation = location
                    if (location.horizontalAccuracy < 0) return@forEach
                    val point = location.toPosition()
                    // Suspend emission instead of silently dropping a burst while SQLite commits.
                    mainScope.launch { positions.emit(point) }
                    val anchor = state.value.lastAcceptedLocation
                    if (state.value.paused && anchor?.latitude != null && anchor.longitude != null &&
                        location.distanceFromLocation(CLLocation(anchor.latitude, anchor.longitude)) > locationConfig.stationaryRadiusMeters) {
                        mainScope.launch { signals.emit(Signal.StationaryExit) }
                    }
                    pendingLocation?.complete(location)
                }
            }

            override fun locationManagerDidPauseLocationUpdates(manager: CLLocationManager) {
                mainScope.launch { signals.emit(Signal.StationaryEnter) }
            }

            override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
                pendingLocation?.complete(null)
            }

            override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
                if (manager.authorizationStatus != kCLAuthorizationStatusAuthorizedAlways &&
                    manager.authorizationStatus != kCLAuthorizationStatusAuthorizedWhenInUse) {
                    manager.stopUpdatingLocation()
                }
            }
        }
        delegate = newDelegate

        val newManager = CLLocationManager().apply {
            this.delegate = newDelegate
            desiredAccuracy = locationConfig.accuracy.toIosAccuracy()
            distanceFilter = kCLDistanceFilterNone
            allowsBackgroundLocationUpdates = true
            pausesLocationUpdatesAutomatically = true
        }

        val authorized = when (newManager.authorizationStatus) {
            kCLAuthorizationStatusAuthorizedAlways,
            kCLAuthorizationStatusAuthorizedWhenInUse -> true
            kCLAuthorizationStatusDenied,
            kCLAuthorizationStatusRestricted -> false
            else -> false // Places owns all permission prompts.

        }

        if (!authorized) {
            Log.log("Location permission denied")
            newManager.delegate = null
            delegate = null
            return
        }

        newManager.startMonitoringSignificantLocationChanges()
        if (standard) newManager.startUpdatingLocation()
        manager = newManager
        Log.log("Location updates started")
    }

    @OptIn(ExperimentalForeignApi::class)
    private suspend fun ensureStopped(awaitFinalFix: Boolean) {
        val current = manager ?: return
        current.stopUpdatingLocation()
        // Keep passive recovery alive without another GPS fix when stationary.
        if (awaitFinalFix) return
        current.stopMonitoringSignificantLocationChanges()
        current.delegate = null
        Log.log("Location updates stopped")
        manager = null
        delegate = null
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun fetchOnce(): Position? = withContext(Dispatchers.Main) {
        manager?.let { return@withContext fetchOnce(it) }

        val newDelegate = object : NSObject(), CLLocationManagerDelegateProtocol {
            override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
                didUpdateLocations.forEach { value ->
                    pendingLocation?.complete(value as CLLocation)
                }
            }

            override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
                pendingLocation?.complete(null)
            }
        }
        val transient = CLLocationManager().apply {
            delegate = newDelegate
            desiredAccuracy = locationConfig.accuracy.toIosAccuracy()
            allowsBackgroundLocationUpdates = true
        }
        try {
            fetchOnce(transient)
        } finally {
            transient.delegate = null
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private suspend fun fetchOnce(active: CLLocationManager): Position? {
        val deferred = CompletableDeferred<CLLocation?>()
        pendingLocation = deferred
        active.requestLocation()
        val fresh = try {
            withTimeoutOrNull(LOCATION_FETCH_TIMEOUT) { deferred.await() }
        } finally {
            pendingLocation = null
        }
        return (fresh ?: active.location)?.toPosition()
    }
}

private fun Accuracy.toIosAccuracy(): Double = when (this) {
    Accuracy.HIGHEST -> kCLLocationAccuracyBestForNavigation
    Accuracy.HIGH -> kCLLocationAccuracyBest
    Accuracy.MEDIUM -> kCLLocationAccuracyHundredMeters
    Accuracy.LOW -> kCLLocationAccuracyKilometer
}

@OptIn(ExperimentalForeignApi::class)
private fun CLLocation.toPosition(): Position {
    val (latitude, longitude) = coordinate.useContents { latitude to longitude }
    val timestamp: NSDate = timestamp
    return Position(
        latitude = latitude,
        longitude = longitude,
        accuracy = horizontalAccuracy,
        time = (timestamp.timeIntervalSince1970 * 1000).toLong(),
        altitude = if (verticalAccuracy >= 0) altitude else null,
        speed = if (speed >= 0) speed else null,
        bearing = if (course >= 0) course else null,
    )
}
