package org.blackaddons.core.control

import org.blackaddons.api.player.LeaseState
import org.blackaddons.api.player.MouseButton
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HoldsTest {

    private val edges = mutableListOf<Pair<MouseButton, Boolean>>()
    private val holds = Holds<MouseButton> { button, held -> edges += button to held }

    @Test
    fun `a button stays down until the last holder lets go`() {
        val clicker = holds.hold(MouseButton.ATTACK, "clicker")
        val action = holds.hold(MouseButton.ATTACK, "action")
        action.release()
        assertTrue(holds.isHeld(MouseButton.ATTACK), "one feature letting go must not lift another's hold")
        clicker.release()
        assertFalse(holds.isHeld(MouseButton.ATTACK))
        assertEquals(listOf(MouseButton.ATTACK to true, MouseButton.ATTACK to false), edges)
    }

    @Test
    fun `the same owner holding again replaces its hold without a release edge`() {
        val first = holds.hold(MouseButton.USE, "clicker")
        holds.hold(MouseButton.USE, "clicker")
        assertEquals(LeaseState.RELEASED, first.state)
        assertEquals(listOf(MouseButton.USE to true), edges)
    }

    @Test
    fun `clear lets every button go`() {
        val hold = holds.hold(MouseButton.USE, "clicker")
        holds.clear()
        assertEquals(LeaseState.REVOKED, hold.state)
        assertFalse(holds.isHeld(MouseButton.USE))
    }
}
