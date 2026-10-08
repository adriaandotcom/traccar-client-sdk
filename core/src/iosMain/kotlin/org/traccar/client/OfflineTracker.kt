package org.traccar.client

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job

/** Explicit host lifecycle: construct only after protected local storage is available. */
class OfflineTracker(output: OfflineOutput, lastPosition: Position?, initiallyPaused: Boolean) {
    private val scope = ComponentCoroutineScope()
    private val config = Config()
    private val state = StateStore(State(paused = initiallyPaused && lastPosition != null, lastAcceptedLocation = lastPosition))
    private val source = IosLocationSource(scope, config, state.state)
    private val motion = MotionActivityDetector(scope, config, state.state)
    private val region = RegionDetector(scope, config, state.state, source.positions)
    private val engine = TrackerEngine(state, output, source, listOf(motion, region, source),
        listOf(LocationFilter(config, state), IosBatteryProcessor()), scope)

    suspend fun start() { state.update { it.copy(enabled = true) } }
    suspend fun stop() { state.update { it.copy(enabled = false) } }
    suspend fun close() { stop(); scope.coroutineContext.job.cancelAndJoin() }
}
