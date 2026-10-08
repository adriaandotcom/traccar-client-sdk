package org.traccar.client

import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

// Places offline fork: LocationSource -> LocationFilter -> TrackerEngine -> OfflineOutput.
// The output is permanent host SQLite storage. There is no queue, upload or network monitor.
class TrackerEngine internal constructor(
    private val stateStore: StateStore,
    private val output: OfflineOutput,
    private val locationSource: LocationSource,
    signalSources: List<SignalSource>,
    private val processors: List<PositionProcessor>,
    scope: ComponentCoroutineScope,
) {
    private val mutex = Mutex()
    init {
        scope.launch { signalSources.map { it.signals }.merge().collect { handle(it) } }
        scope.launch {
            locationSource.positions.collect { incoming ->
                if (!stateStore.state.value.enabled) return@collect
                var current: Position? = incoming
                for (processor in processors) { current = processor.process(current ?: break) }
                val final = current ?: return@collect
                val committed = suspendCancellableCoroutine { continuation ->
                    output.save(final) { success -> if (continuation.isActive) continuation.resume(success) }
                }
                if (!committed) {
                    stateStore.update { it.copy(enabled = false) }
                    output.storageFailed()
                }
            }
        }
    }
    suspend fun handle(signal: Signal) = mutex.withLock {
        val state = stateStore.state.value
        if (!state.enabled) return@withLock
        when (signal) {
            Signal.StationaryEnter -> if (!state.paused) {
                stateStore.update { it.copy(paused = true) }
                output.stateChanged(true)
            }
            Signal.StationaryExit -> if (state.paused) {
                stateStore.update { it.copy(paused = false) }
                output.stateChanged(false)
            }
            Signal.HeartbeatTick -> Unit // Deliberately unsupported in the offline fork.
        }
    }
}
