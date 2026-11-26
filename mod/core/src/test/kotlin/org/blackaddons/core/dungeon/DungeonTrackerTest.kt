package org.blackaddons.core.dungeon

import org.blackaddons.api.chat.ChatMessage
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.chat.ChatMessageType
import org.blackaddons.api.event.chat.ChatReceiveEvent
import org.blackaddons.api.event.dungeon.DungeonPlayerDeathEvent
import org.blackaddons.api.event.subscribe
import org.blackaddons.api.skyblock.SkyblockHost
import org.blackaddons.api.skyblock.dungeon.*
import org.blackaddons.api.testing.TestPlatform
import org.blackaddons.core.dungeon.map.MapState
import org.blackaddons.core.dungeon.map.RoomData
import org.blackaddons.core.dungeon.map.RoomTile
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DungeonTrackerTest {

    @BeforeEach
    fun setUp() {
        TestPlatform.skyblock = object : SkyblockHost {
            override val onSkyblock: Boolean = true
            override val location: String = "The Catacombs"
            override val profile: String? = null
            override val accountId: String? = null
            override val dungeonFloor: DungeonFloor = DungeonFloor.F7
            override val inRift: Boolean = false
        }
        DungeonTracker.start()
        DungeonTracker.reset()
    }

    @Test
    fun `teammate lookup is case insensitive`() {
        DungeonTracker.dungeonTeammates.add(
            DungeonPlayer(
                name = "TestPlayer",
                dungeonClass = DungeonClass.MAGE,
                classLevel = 50,
            )
        )

        val found = DungeonTracker.teammate("testplayer")
        assertTrue(found != null)
        assertEquals("TestPlayer", found?.name)
        assertEquals(DungeonClass.MAGE, found?.dungeonClass)
        assertEquals(50, found?.classLevel)
    }

    @Test
    fun `watcher messages update blood room state`() {
        MapState.reset()
        val tile = RoomTile(0, 0, RoomData("Blood", RoomType.BLOOD, RoomShape.S1x1, emptyList()))
        tile.addToUnique(0, 0)

        try {
            assertEquals(RoomState.UNDISCOVERED, tile.state)

            EventBus.post(chat("[BOSS] The Watcher: That will be enough for now."))
            assertEquals(RoomState.CLEARED, tile.state)

            EventBus.post(chat("[BOSS] The Watcher: You have proven yourself. You may pass."))
            assertEquals(RoomState.GREEN, tile.state)
        } finally {
            MapState.reset()
        }
    }

    @Test
    fun `death messages retain the leading space required by the Hypixel format`() {
        var death: DungeonPlayerDeathEvent? = null
        val listener = EventBus.subscribe<DungeonPlayerDeathEvent> { death = event }

        try {
            EventBus.post(chat(" ☠ Teammate was killed by a Zombie."))
            assertEquals("Teammate", death?.username)
            assertEquals("was killed by a Zombie", death?.reason)
        } finally {
            listener.unregister()
        }
    }

    /** From the packet, so chat cleaners can't starve the tracker. */
    private fun chat(text: String) =
        ChatReceiveEvent(text, text, ChatMessageType.SYSTEM) { ChatMessage(emptyList()) }

    @Test
    fun `alive tab entry clears a teammates dead state`() {
        DungeonTracker.updateTeammate("[30] Teammate (DEAD)")
        assertTrue(DungeonTracker.teammate("Teammate")?.isDead == true)

        DungeonTracker.updateTeammate("[30] Teammate (Mage 50)")
        assertFalse(DungeonTracker.teammate("Teammate")?.isDead == true)
    }
}
