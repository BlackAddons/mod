package org.blackaddons.core.control

import org.blackaddons.api.feature.Feature
import org.blackaddons.api.player.LeaseState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class LeaseArbiterTest {

    private val log = mutableListOf<String>()
    private val arbiter = LeaseArbiter(object : LeaseArbiter.Events<TestLease> {
        override fun granted(lease: TestLease) { log += "grant ${lease.name}" }
        override fun ended(lease: TestLease) { log += "end ${lease.name}" }
    })

    private inner class TestLease(val name: String, owner: Any, priority: Int = 0) : LeaseArbiter.Entry(owner, priority) {
        override fun release() = arbiter.release(this)
    }

    private fun lease(name: String, owner: Any = name, priority: Int = 0) =
        TestLease(name, owner, priority).also { arbiter.request(it) }

    @Test
    fun `a second owner waits in line until the first lets go`() {
        val a = lease("a")
        val b = lease("b")
        assertEquals(LeaseState.ACTIVE, a.state)
        assertEquals(LeaseState.WAITING, b.state)

        a.release()
        assertEquals(LeaseState.RELEASED, a.state)
        assertSame(b, arbiter.active)
        assertEquals(listOf("grant a", "end a", "grant b"), log)
    }

    @Test
    fun `a higher priority takes over and the holder is revoked`() {
        val a = lease("a")
        val high = lease("high", priority = 1)
        assertEquals(LeaseState.REVOKED, a.state)
        assertSame(high, arbiter.active)
    }

    @Test
    fun `the same owner replaces its lease without anyone slipping in between`() {
        lease("a1", owner = "a")
        val b = lease("b")
        val a2 = lease("a2", owner = "a")
        assertSame(a2, arbiter.active)
        assertEquals(LeaseState.WAITING, b.state)
    }

    @Test
    fun `waiting leases are served by priority, then by arrival`() {
        val holder = lease("holder", priority = 2)
        val low = lease("low")
        val first = lease("first", priority = 1)
        val second = lease("second", priority = 1)
        holder.release()
        assertSame(first, arbiter.active)
        first.release()
        assertSame(second, arbiter.active)
        second.release()
        assertSame(low, arbiter.active)
    }

    @Test
    fun `nothing is granted while held, and resume serves the next in line`() {
        val a = lease("a")
        val b = lease("b")
        arbiter.held = true
        a.release()
        assertNull(arbiter.active)
        assertEquals(LeaseState.WAITING, b.state)
        arbiter.resume()
        assertSame(b, arbiter.active)
    }

    private class TestFeature : Feature(toggled = true)

    @Test
    fun `a switched off owner loses its lease`() {
        val feature = TestFeature()
        val active = lease("active", owner = feature)
        val waiting = lease("waiting", owner = "other")
        feature.enabled = false
        arbiter.tick()
        assertEquals(LeaseState.REVOKED, active.state)
        assertSame(waiting, arbiter.active)
    }

    @Test
    fun `clear revokes everything`() {
        val a = lease("a")
        val b = lease("b")
        arbiter.clear()
        assertEquals(LeaseState.REVOKED, a.state)
        assertEquals(LeaseState.REVOKED, b.state)
        assertNull(arbiter.active)
    }
}
