package org.traccar.client
// No SDK log database or console output. In particular, never persist coordinates in logs.
internal object Log { fun log(message: String) {} }
