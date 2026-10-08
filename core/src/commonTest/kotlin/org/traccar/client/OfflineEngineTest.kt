package org.traccar.client

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineEngineTest {
    private class Source : LocationSource {
        override val positions = MutableSharedFlow<Position>(extraBufferCapacity = 8)
        override suspend fun fetchOnce(): Position? = null
    }
    private class Output : OfflineOutput {
        val points = mutableListOf<Position>()
        val states = mutableListOf<Boolean>()
        var fail = false
        var failed = false
        override fun save(position: Position, completion: (Boolean) -> Unit) {
            if (!fail) points.add(position)
            completion(!fail)
        }
        override fun stateChanged(paused: Boolean) { states.add(paused) }
        override fun storageFailed() { failed = true }
    }
    @Test fun savesAcceptedLocationsWithoutNetworkAndStopsOnStorageFailure() = runTest {
        val scope = ComponentCoroutineScope(backgroundScope.coroutineContext)
        val state = StateStore(State(enabled = true))
        val source = Source(); val output = Output()
        TrackerEngine(state, output, source, emptyList(), listOf(LocationFilter(Config(), state)), scope)
        runCurrent()
        val first = Position(latitude = 52.36, longitude = 4.88, accuracy = 10.0, time = 1000)
        source.positions.emit(first); runCurrent()
        source.positions.emit(first.copy(time = 2000)); runCurrent()
        assertEquals(listOf(first), output.points)
        output.fail = true
        source.positions.emit(first.copy(latitude = 52.37, time = 3000)); runCurrent()
        assertTrue(output.failed)
        assertFalse(state.state.value.enabled)
        assertEquals(1, output.points.size)
    }
    @Test fun stationarySignalsPauseAndWakeOnlyEnabledTracker() = runTest {
        val scope = ComponentCoroutineScope(backgroundScope.coroutineContext)
        val state = StateStore(State(enabled = true)); val output = Output()
        val engine = TrackerEngine(state, output, Source(), emptyList(), emptyList(), scope)
        engine.handle(Signal.StationaryEnter)
        assertTrue(state.state.value.paused)
        engine.handle(Signal.StationaryExit)
        assertFalse(state.state.value.paused)
        state.update { it.copy(enabled = false) }
        engine.handle(Signal.StationaryEnter)
        assertFalse(state.state.value.paused)
        assertEquals(listOf(true, false), output.states)
    }
}
