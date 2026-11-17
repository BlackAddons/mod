package org.blackaddons.core.control

import org.blackaddons.api.event.input.MovementInputEvent
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.player.DrivePriority
import org.blackaddons.api.player.LeaseState
import org.blackaddons.api.player.MoveKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class MovementControlTest {

    private val control = MovementControl()

    private fun playerHolding(vararg keys: MoveKey) = MovementInputEvent(
        false, false, false, false, false, false, false, 0f, 0f,
    ).apply { press(keys.toSet()) }

    private fun applied(input: MovementInputEvent = playerHolding()): MovementInputEvent = input.also(control::apply)

    @Test
    fun `without a drive the player's own keys pass`() {
        assertEquals(setOf(MoveKey.LEFT), applied(playerHolding(MoveKey.LEFT)).pressed)
    }

    @Test
    fun `the strongest steering drive replaces the player's keys`() {
        control.drive("freecam", DrivePriority.LOW) { emptySet() }
        control.drive("actions", DrivePriority.HIGH) { setOf(MoveKey.FORWARD, MoveKey.SNEAK) }
        control.drive("relic", DrivePriority.NORMAL) { setOf(MoveKey.BACKWARD) }
        val input = applied(playerHolding(MoveKey.LEFT, MoveKey.JUMP))
        assertEquals(setOf(MoveKey.FORWARD, MoveKey.SNEAK), input.pressed)
        assertEquals(1f, input.forwardImpulse)
        assertEquals(0f, input.leftImpulse)
    }

    @Test
    fun `a drive that does not steer this tick hands over without being asked again later`() {
        var actionsSteer: Set<MoveKey>? = setOf(MoveKey.FORWARD)
        var freecamAsked = 0
        control.drive("freecam", DrivePriority.LOW) { freecamAsked++; emptySet() }
        control.drive("actions", DrivePriority.HIGH) { actionsSteer }
        applied()
        assertEquals(0, freecamAsked, "a weaker drive isn't asked while a stronger one steers")
        actionsSteer = null
        assertEquals(emptySet<MoveKey>(), applied(playerHolding(MoveKey.FORWARD)).pressed, "the preempted drive is back")
    }

    @Test
    fun `same priority goes to the older drive`() {
        control.drive("first", DrivePriority.NORMAL) { setOf(MoveKey.LEFT) }
        control.drive("second", DrivePriority.NORMAL) { setOf(MoveKey.RIGHT) }
        assertEquals(setOf(MoveKey.LEFT), applied().pressed)
    }

    @Test
    fun `a newer drive from the same owner replaces the older`() {
        val old = control.drive("relic", DrivePriority.NORMAL) { setOf(MoveKey.LEFT) }
        control.drive("relic", DrivePriority.NORMAL) { setOf(MoveKey.RIGHT) }
        assertEquals(LeaseState.RELEASED, old.state)
        assertEquals(setOf(MoveKey.RIGHT), applied().pressed)
    }

    @Test
    fun `a released drive stops steering`() {
        val drive = control.drive("relic", DrivePriority.NORMAL) { setOf(MoveKey.FORWARD) }
        drive.release()
        assertEquals(setOf(MoveKey.JUMP), applied(playerHolding(MoveKey.JUMP)).pressed)
    }

    @Test
    fun `a steer that throws is revoked and the next drive steers`() {
        val broken = control.drive("broken", DrivePriority.HIGH) { error("boom") }
        control.drive("fine", DrivePriority.NORMAL) { setOf(MoveKey.BACKWARD) }
        assertEquals(setOf(MoveKey.BACKWARD), applied().pressed)
        assertEquals(LeaseState.REVOKED, broken.state)
    }

    private class TestFeature : Feature(toggled = true)

    @Test
    fun `a switched off owner's drive and holds are revoked`() {
        val feature = TestFeature()
        val drive = control.drive(feature, DrivePriority.NORMAL) { setOf(MoveKey.FORWARD) }
        val hold = control.hold(MoveKey.SNEAK, feature)
        feature.enabled = false
        control.tick()
        assertEquals(LeaseState.REVOKED, drive.state)
        assertEquals(LeaseState.REVOKED, hold.state)
        assertEquals(emptySet<MoveKey>(), applied().pressed)
    }

    @Test
    fun `holds add to whatever moves the player`() {
        control.drive("actions", DrivePriority.HIGH) { setOf(MoveKey.FORWARD) }
        val hold = control.hold(MoveKey.SNEAK, "chest")
        assertEquals(setOf(MoveKey.FORWARD, MoveKey.SNEAK), applied().pressed)
        hold.release()
        assertFalse(MoveKey.SNEAK in applied().pressed)
    }

    @Test
    fun `a world change drops holds but keeps drives`() {
        control.drive("freecam", DrivePriority.LOW) { emptySet() }
        val hold = control.hold(MoveKey.SNEAK, "chest")
        control.onWorldChange()
        assertEquals(LeaseState.REVOKED, hold.state)
        assertEquals(emptySet<MoveKey>(), applied(playerHolding(MoveKey.FORWARD)).pressed)
    }
}
