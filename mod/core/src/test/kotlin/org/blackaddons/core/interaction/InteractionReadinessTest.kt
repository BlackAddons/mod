package org.blackaddons.core.interaction

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class InteractionReadinessTest {
    @Test
    fun `interaction waits for a sent rotation and stays before movement`() {
        val readiness = InteractionReadiness()

        readiness.onTickStart()
        assertFalse(readiness.canPerform(90f, 20f, awaitingRotationSync = false))

        readiness.onRotationSent(90f, 20f)
        assertFalse(readiness.canPerform(90f, 20f, awaitingRotationSync = true))
        assertFalse(readiness.canPerform(91f, 20f, awaitingRotationSync = false))
        assertTrue(readiness.canPerform(90f, 20f, awaitingRotationSync = false))

        readiness.onPositionSent()
        assertFalse(readiness.canPerform(90f, 20f, awaitingRotationSync = false))
        readiness.onTickStart()
        assertTrue(readiness.canPerform(90f, 20f, awaitingRotationSync = false))

        readiness.reset()
        readiness.onTickStart()
        assertFalse(readiness.canPerform(90f, 20f, awaitingRotationSync = false))
    }

    @Test
    fun `the click rotation is the last sent one, only before movement`() {
        val readiness = InteractionReadiness()

        readiness.onTickStart()
        assertNull(readiness.clickRotation())

        readiness.onRotationSent(90f, 20f)
        assertEquals(90f to 20f, readiness.clickRotation())

        readiness.onPositionSent()
        assertNull(readiness.clickRotation())
        readiness.onTickStart()
        assertEquals(90f to 20f, readiness.clickRotation())

        readiness.reset()
        readiness.onTickStart()
        assertNull(readiness.clickRotation())
    }

    @Test
    fun `a second block use waits for the next movement interval`() {
        val readiness = InteractionReadiness()

        assertTrue(readiness.canUseBlock())
        readiness.onBlockUseSent()
        assertFalse(readiness.canUseBlock())

        readiness.onPositionSent()
        assertTrue(readiness.canUseBlock())

        readiness.onBlockUseSent()
        readiness.reset()
        assertTrue(readiness.canUseBlock())
    }

    @Test
    fun `deferred clicks coalesce and replay once after position is sent`() {
        val readiness = InteractionReadiness()
        readiness.deferAttack()
        readiness.deferAttack()
        readiness.deferUse()

        assertEquals(InteractionReadiness.DeferredClicks(attack = true, use = true), readiness.onPositionSent())
        assertEquals(InteractionReadiness.DeferredClicks(attack = false, use = false), readiness.onPositionSent())

        readiness.deferUse()
        readiness.reset()
        assertFalse(readiness.onPositionSent().use)
    }

    @Test
    fun `the last yaw change is measured across the wrap and cleared on reset`() {
        val readiness = InteractionReadiness()

        readiness.onRotationSent(179f, 0f)
        assertEquals(0f, readiness.lastYawChange)
        readiness.onRotationSent(-178f, 5f)
        assertEquals(3f, readiness.lastYawChange, 0.001f)

        readiness.reset()
        assertEquals(0f, readiness.lastYawChange)
    }
}
