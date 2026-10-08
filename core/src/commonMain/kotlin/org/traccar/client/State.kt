package org.traccar.client


data class State(
    val enabled: Boolean = false,
    val paused: Boolean = false,
    val lastAcceptedLocation: Position? = null,
)
