package org.blackaddons.core.tps

import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.network.ServerTimeUpdateEvent

/**
 * Server TPS from the cadence of `ClientboundSetTimePacket` (one per 20 ticks): `TPS = 20 / (avg_ms / 1000)`. Hooked in
 * `TpsTrackerMixin`. Written on the client thread and read on the render thread, so synchronized.
 */
object TpsTracker {

    private const val WINDOW = 5
    private val deltas = ArrayDeque<Long>(WINDOW)
    private var lastTimestamp = 0L

    fun recordTick() {
        val now = System.currentTimeMillis()
        synchronized(deltas) {
            if (lastTimestamp != 0L) {
                val d = now - lastTimestamp
                if (d in 1..10_000) {
                    if (deltas.size >= WINDOW) deltas.removeFirst()
                    deltas.addLast(d)
                }
            }
            lastTimestamp = now
        }
        EventBus.post(ServerTimeUpdateEvent(now))
    }

    fun current(): Double? {
        val avg = synchronized(deltas) {
            if (deltas.isEmpty()) return null
            deltas.average()
        }
        if (avg <= 0) return null
        return (20_000.0 / avg).coerceAtMost(20.0)
    }

    fun reset() = synchronized(deltas) {
        deltas.clear()
        lastTimestamp = 0L
    }
}
