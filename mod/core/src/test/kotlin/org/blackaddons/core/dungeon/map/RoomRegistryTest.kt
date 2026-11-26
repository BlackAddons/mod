package org.blackaddons.core.dungeon.map

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RoomRegistryTest {
    @Test
    fun `bundled native hashes identify every collected room without collisions`() {
        RoomRegistry.init()

        val rooms = RoomRegistry.allRooms()
        val cores = rooms.flatMap { it.cores }

        assertEquals(140, rooms.size)
        assertEquals(240, cores.size)
        assertEquals(58, rooms.sumOf { it.secretCoords.lever.size })
        assertEquals(cores.size, cores.distinct().size)
        for (room in rooms) for (core in room.cores) {
            assertSame(room, RoomRegistry.getRoomData(core))
        }
    }

    @Test
    fun `served room document uses the current hash contract`() {
        val rooms = RoomRegistry::class.java.getResourceAsStream("/assets/blackaddons/rooms.json")!!
            .bufferedReader().use { JsonParser.parseReader(it) }
        val document = JsonObject().apply {
            addProperty("algorithm", ModernRoomCore.ALGORITHM)
            add("rooms", rooms)
        }

        assertEquals(140, RoomRegistry.parse(document).size)

        document.addProperty("algorithm", "some-other-hash")
        assertThrows(IllegalArgumentException::class.java) { RoomRegistry.parse(document) }
    }

    @Test
    fun `Prince rooms match the dungeon room catalog`() {
        RoomRegistry.init()

        assertEquals(
            setOf(
                "Big Red Flag", "Chambers", "Doors", "Flags", "Grass Ruin",
                "Leaves", "Market", "Pirate", "Quartz Knight", "Red Blue",
                "Skull", "Sloth", "Supertall", "Waterfall", "Withermancer",
            ),
            RoomRegistry.allRooms().filter { it.containsPrince }.mapTo(linkedSetOf()) { it.name },
        )
    }
}
