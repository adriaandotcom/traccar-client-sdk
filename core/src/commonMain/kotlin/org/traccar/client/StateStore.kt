package org.traccar.client
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// Places owns persistence. Raw positions are committed by OfflineOutput before acceptance.
class StateStore internal constructor(initial: State = State()) {
    private val mutable = MutableStateFlow(initial)
    val state = mutable.asStateFlow()
    suspend fun update(transform: (State) -> State) { mutable.update(transform) }
}
