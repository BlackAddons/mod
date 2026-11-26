package org.blackaddons.cheat.features.impl.cheats.invWalk

import org.blackaddons.api.event.world.ClickKind
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class InvWalkFlowTest {

    private val pick = HeldClick(10, 0, ClickKind.PICKUP)
    private val place = HeldClick(20, 0, ClickKind.PICKUP)

    private companion object {
        const val RESUME = 5
    }

    private fun openInventory() = InvWalkFlow().apply { open(WalkScreen.INVENTORY) }

    @Test
    fun `a click while moving waits until the server heard the stop, then goes out in order`() {
        val flow = openInventory()
        assertFalse(flow.onClick(pick, still = false))
        assertFalse(flow.onClick(place, still = false))
        assertFalse(flow.walking)
        assertEquals(WalkStep.None, flow.tick(still = false, itemsClear = false, RESUME))
        assertEquals(WalkStep.Send(listOf(pick, place), chat = false), flow.tick(still = true, itemsClear = false, RESUME))
        assertTrue(flow.onClick(pick, still = true))
    }

    @Test
    fun `walking comes back with a close once the cursor stays empty`() {
        val flow = openInventory()
        assertTrue(flow.onClick(pick, still = true))
        assertEquals(WalkStep.None, flow.tick(still = true, itemsClear = false, RESUME))
        repeat(RESUME - 1) { assertEquals(WalkStep.None, flow.tick(still = true, itemsClear = true, RESUME)) }
        assertEquals(WalkStep.Close, flow.tick(still = true, itemsClear = true, RESUME))
        assertTrue(flow.walking)
        assertTrue(flow.keepCloseBack())
    }

    @Test
    fun `the game's close is kept back only while the server never saw a click`() {
        val flow = openInventory()
        assertTrue(flow.keepCloseBack())
        flow.onClick(pick, still = true)
        assertFalse(flow.keepCloseBack())
        flow.reset()
        assertFalse(flow.keepCloseBack())
    }

    @Test
    fun `held clicks go out anyway when the player never stops`() {
        val flow = openInventory()
        flow.onClick(pick, still = false)
        repeat(InvWalkFlow.MAX_STOP_TICKS - 1) { assertEquals(WalkStep.None, flow.tick(still = false, itemsClear = true, RESUME)) }
        assertEquals(WalkStep.Send(listOf(pick), chat = false), flow.tick(still = false, itemsClear = true, RESUME))
    }

    @Test
    fun `clicks outside a walk screen pass untouched`() {
        val flow = InvWalkFlow()
        assertTrue(flow.onClick(pick, still = false))
        assertEquals(WalkStep.None, flow.tick(still = false, itemsClear = true, RESUME))
    }

    @Test
    fun `a command while moving waits for the stop, after the clicks before it`() {
        val flow = openInventory()
        assertFalse(flow.onClick(pick, still = false))
        assertFalse(flow.onChat(still = false))
        assertEquals(WalkStep.Send(listOf(pick), chat = true), flow.tick(still = true, itemsClear = true, RESUME))
    }

    @Test
    fun `chat alone opens nothing for the server, so walking comes back without a close`() {
        val flow = openInventory()
        assertFalse(flow.onChat(still = false))
        assertEquals(WalkStep.Send(emptyList(), chat = true), flow.tick(still = true, itemsClear = true, RESUME))
        assertEquals(WalkStep.None, flow.tick(still = true, itemsClear = true, RESUME))
        assertTrue(flow.walking)
        assertTrue(flow.keepCloseBack())
    }

    @Test
    fun `a resume delay of 0 closes on the first tick the cursor is empty`() {
        val flow = openInventory()
        flow.onClick(pick, still = true)
        assertEquals(WalkStep.Close, flow.tick(still = true, itemsClear = true, resumeTicks = 0))
    }
}
