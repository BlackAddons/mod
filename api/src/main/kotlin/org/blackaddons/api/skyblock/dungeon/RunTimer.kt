package org.blackaddons.api.skyblock.dungeon

/** RTA, IGT and total desync. Desync adds `interval - 1000ms` when time packets arrive late ([onServerTimeUpdate]). */
class RunTimer {

    companion object {
        private const val TIME_PACKET_INTERVAL_MS = 1000L
    }

    private val lock = Any()

    private var startTimeMs = 0L
    private var lastTimePacket = 0L
    private var totalDesyncMs = 0L

    @Volatile
    var running: Boolean = false
        private set

    @Volatile
    var completed: Boolean = false
        private set

    private var finalRtaMs = 0L
    private var finalIgtMs = 0L
    private var finalDesyncMs = 0L

    fun start() {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            startTimeMs = now
            lastTimePacket = now
            totalDesyncMs = 0L
            running = true
            completed = false
            finalRtaMs = 0L
            finalIgtMs = 0L
            finalDesyncMs = 0L
        }
    }

    fun complete() {
        if (!running) return
        val now = System.currentTimeMillis()
        synchronized(lock) {
            finalDesyncMs = totalDesyncMs
            finalRtaMs = (now - startTimeMs).coerceAtLeast(0L)
            finalIgtMs = (finalRtaMs - finalDesyncMs).coerceAtLeast(0L)
            running = false
            completed = true
        }
    }

    fun reset() {
        synchronized(lock) {
            startTimeMs = 0L
            lastTimePacket = 0L
            totalDesyncMs = 0L
            running = false
            completed = false
            finalRtaMs = 0L
            finalIgtMs = 0L
            finalDesyncMs = 0L
        }
    }

    fun onServerTimeUpdate(now: Long = System.currentTimeMillis()) {
        if (!running) return
        synchronized(lock) {
            if (lastTimePacket > 0L) {
                val diff = now - lastTimePacket
                if (diff > TIME_PACKET_INTERVAL_MS) {
                    totalDesyncMs += (diff - TIME_PACKET_INTERVAL_MS)
                }
            }
            lastTimePacket = now
        }
    }

    val rtaMs: Long
        get() {
            if (completed) return finalRtaMs
            if (!running) return 0L
            return (System.currentTimeMillis() - startTimeMs).coerceAtLeast(0L)
        }

    val igtMs: Long
        get() {
            if (completed) return finalIgtMs
            if (!running) return 0L
            return (rtaMs - desyncMs).coerceAtLeast(0L)
        }

    val desyncMs: Long
        get() {
            if (completed) return finalDesyncMs
            if (!running) return 0L
            return synchronized(lock) { totalDesyncMs }
        }
}
