package org.blackaddons.api.util

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerClockTest {

    private var now = 1_000L
    private val clock = ServerClock { now }

    @Test
    fun `a deadline needs its server ticks`() {
        val deadline = clock.after(3)
        now += 10_000
        repeat(2) { clock.tick() }
        assertFalse(clock.passed(deadline), "a stalled server sends no ticks, so the wait goes on")
        clock.tick()
        assertTrue(clock.passed(deadline))
    }

    @Test
    fun `pings faster than ticks cannot shorten the wait`() {
        val deadline = clock.after(12)
        repeat(36) { clock.tick() }
        now += 599
        assertFalse(clock.passed(deadline))
        now += 1
        assertTrue(clock.passed(deadline))
    }

    @Test
    fun `zero ticks has already passed`() {
        assertTrue(clock.passed(clock.after(0)))
    }
}
