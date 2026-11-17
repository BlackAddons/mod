package org.blackaddons.api.host

/** Live counters; read fresh each frame. */
interface GameStats {
    val fps: Int

    /** Milliseconds; 0 when not connected. */
    val ping: Int

    /**
     * Server ticks per second, from the time sync packets. Null until a few have arrived since the last world
     * change.
     */
    val tps: Double?

    /** Epoch ms of the last received packet, 0 if none. */
    val lastPacketTimeMs: Long

    /** Sends a ping and calls [callback] with the latency in ms, or -1 if not connected or timed out. */
    fun measureActivePing(callback: (Int) -> Unit)
}
