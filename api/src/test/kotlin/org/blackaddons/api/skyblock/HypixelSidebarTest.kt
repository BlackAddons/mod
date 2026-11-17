package org.blackaddons.api.skyblock

import org.blackaddons.api.skyblock.dungeon.DungeonFloor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Sidebar and tab-list parsing gates every "where am I" check: exact Hypixel lines in, null for anything else. */
class HypixelSidebarTest {

    @Test
    fun `dungeon floor reads the first tagged line`() {
        val lines = listOf(
            "The Catacombs",
            "§7⏣ §cThe Catacombs §7(F7)",
            "Something (M3) else",
        )
        assertEquals(DungeonFloor.F7, HypixelSidebar.dungeonFloor(lines))
    }

    @Test
    fun `dungeon floor ignores a queued run`() {
        assertNull(HypixelSidebar.dungeonFloor(listOf("Queued: The Catacombs (F7)")))
        assertEquals(DungeonFloor.M7, HypixelSidebar.dungeonFloor(listOf("Queue: The Catacombs (F7)", "The Catacombs (M7)")))
    }

    @Test
    fun `dungeon floor recognizes Entrance and master mode`() {
        assertEquals(DungeonFloor.ENTRANCE, HypixelSidebar.dungeonFloor(listOf("The Catacombs (Entrance)")))
        assertEquals(DungeonFloor.ENTRANCE, HypixelSidebar.dungeonFloor(listOf("The Catacombs (E)")))
        assertEquals(DungeonFloor.ENTRANCE, HypixelSidebar.dungeonFloor(listOf("The Catacombs (F0)")))
        assertEquals(DungeonFloor.M7, HypixelSidebar.dungeonFloor(listOf("The Catacombs (M7)")))
    }

    @Test
    fun `dungeon floor is null without a floor tag or out of range`() {
        assertNull(HypixelSidebar.dungeonFloor(listOf("Hub", "Your Island")))
        assertNull(HypixelSidebar.dungeonFloor(listOf("The Catacombs (F8)"))) // only 0..7 are floors
        assertNull(HypixelSidebar.dungeonFloor(listOf("The Catacombs (M0)")))
        assertNull(HypixelSidebar.dungeonFloor(emptyList()))
    }

    @Test
    fun `area strips either prefix and trims`() {
        assertEquals("Hub", HypixelSidebar.area(listOf("Profile: Apple", "Area: Hub ")))
        assertEquals("The Catacombs", HypixelSidebar.area(listOf("Dungeon: The Catacombs")))
    }

    @Test
    fun `area takes the first matching line and is null otherwise`() {
        assertEquals("Dwarven Mines", HypixelSidebar.area(listOf("Area: Dwarven Mines", "Area: Hub")))
        assertNull(HypixelSidebar.area(listOf("Profile: Apple", "Not an area line")))
    }

    @Test
    fun `isRift keys off the scoreboard title`() {
        assertTrue(HypixelSidebar.isRift("THE RIFT"))
        assertTrue(HypixelSidebar.isRift("ф The Rift ф")) // decorated, case-insensitive
        assertFalse(HypixelSidebar.isRift("SKYBLOCK"))
        assertFalse(HypixelSidebar.isRift(null))
    }

    @Test
    fun `profile reads the cute name and ignores surrounding text`() {
        assertEquals("Apple", HypixelSidebar.profile(listOf("Nope", "§7Profile: Apple")))
        assertNull(HypixelSidebar.profile(listOf("Area: Hub", "no profile here")))
    }
}
