package org.blackaddons.api.util

/**
 * Counts [ServerTickEvent][org.blackaddons.api.event.ServerTickEvent]s with a wall-clock floor, since some
 * servers send several pings per tick (eu.p3sim.net ~3). A [Deadline] passes only when both have elapsed. The
 * owner calls [tick] from its listener.
 */
class ServerClock(private val wallClock: () -> Long = System::currentTimeMillis) {

    data class Deadline(val tick: Long, val wallMs: Long)

    var ticks = 0L
        private set

    fun tick() {
        ticks++
    }

    /** [serverTicks] ticks, and at least as much wall-clock time, from now. */
    fun after(serverTicks: Int): Deadline = Deadline(ticks + serverTicks, wallClock() + serverTicks * MS_PER_TICK)

    fun passed(deadline: Deadline): Boolean = ticks >= deadline.tick && wallClock() >= deadline.wallMs
}
