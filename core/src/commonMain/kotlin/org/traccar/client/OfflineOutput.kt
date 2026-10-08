package org.traccar.client

/** The host must call completion(true) only after committing to permanent local history.
 * No network fallback or retry queue exists. A failed commit stops the collector and is
 * reported to the host. Callbacks occur on the main thread; completion may be asynchronous.
 */
interface OfflineOutput {
    fun save(position: Position, completion: (Boolean) -> Unit)
    fun stateChanged(paused: Boolean)
    fun storageFailed()
}
