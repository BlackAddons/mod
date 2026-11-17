package org.blackaddons.core.dungeon.map

import org.blackaddons.api.Platform
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.event.dungeon.DungeonTileScannedEvent
import org.blackaddons.api.event.subscribe
import org.blackaddons.api.skyblock.LocationUtils
import org.blackaddons.api.skyblock.dungeon.RoomType

internal object DungeonTree {
    private var splitsCache: Map<UniqueRoom, Set<UniqueRoom>>? = null
    private var bloodRushCache: List<UniqueRoom>? = null
    private var fairyRoom: UniqueRoom? = null
    private var nextRoomAfterFairy: UniqueRoom? = null
    private var roomBeforeFairy: UniqueRoom? = null
    private var currentRoom: UniqueRoom? = null
    private var lastRoom: UniqueRoom? = null

    fun init() {
        EventBus.subscribe<WorldChangeEvent> {
            clearCache()
            currentRoom = null
            lastRoom = null
        }
        EventBus.subscribe<DungeonTileScannedEvent> { clearCache() }
        EventBus.subscribe<ClientTickEvent.Post> { trackRoom() }
    }

    private fun trackRoom() {
        if (!LocationUtils.inDungeon || !Platform.player.isInGame) return
        val room = MapState.getRoomAt(Platform.player.x, Platform.player.z)
        if (room == currentRoom) return
        lastRoom = currentRoom
        currentRoom = room
    }

    fun clearCache() {
        splitsCache = null
        bloodRushCache = null
        fairyRoom = null
        nextRoomAfterFairy = null
        roomBeforeFairy = null
    }

    /** The door out of the fairy room on the rush path, while u are in the room before it or just left it. */
    fun isFairy(door: DoorTile): Boolean {
        val current = currentRoom ?: return false
        getBloodRush()

        val fRoom = fairyRoom ?: return false
        val nextRoom = nextRoomAfterFairy ?: return false
        if (roomBeforeFairy != current && roomBeforeFairy != lastRoom) return false

        val rooms = door.roomTiles.mapNotNull { it.uniqueRoom }
        if (rooms.size != 2) return false
        return (rooms[0] == fRoom && rooms[1] == nextRoom) || (rooms[0] == nextRoom && rooms[1] == fRoom)
    }

    fun nextRushDoor(): DoorTile? {
        val path = getBloodRush()
        val doors = MapState.dungeonList.filterIsInstance<DoorTile>()
        for ((from, to) in path.zipWithNext()) {
            val door = doors.firstOrNull { it.roomTiles.mapNotNull { tile -> tile.uniqueRoom }.toSet() == setOf(from, to) } ?: continue
            if (!door.opened && door.type.needsKey) return door
        }
        return null
    }

    fun getBloodRush(): List<UniqueRoom> = bloodRushCache ?: run {
        val graph = getSplits()
        val start = graph.keys.find { it.data.type == RoomType.ENTRANCE } ?: return emptyList()
        val target = graph.keys.find { it.data.type == RoomType.BLOOD } ?: return emptyList()

        val queue = ArrayDeque<UniqueRoom>()
        val previous = mutableMapOf<UniqueRoom, UniqueRoom?>()

        queue.add(start)
        previous[start] = null

        while (queue.isNotEmpty()) {
            val room = queue.removeFirst()
            if (room == target) break
            graph[room]?.forEach { neighbor ->
                if (neighbor !in previous) {
                    previous[neighbor] = room
                    queue.add(neighbor)
                }
            }
        }

        if (target !in previous) return emptyList()

        val path = mutableListOf<UniqueRoom>()
        var room: UniqueRoom? = target
        while (room != null) {
            path.add(room)
            room = previous[room]
        }

        val reversedPath = path.asReversed()
        bloodRushCache = reversedPath

        val fairyIndex = reversedPath.indexOfFirst { it.data.type == RoomType.FAIRY }
        if (fairyIndex > 0 && fairyIndex < reversedPath.size - 1) {
            roomBeforeFairy = reversedPath[fairyIndex - 1]
            fairyRoom = reversedPath[fairyIndex]
            nextRoomAfterFairy = reversedPath[fairyIndex + 1]
        }

        return reversedPath
    }

    private fun getSplits(): Map<UniqueRoom, Set<UniqueRoom>> = splitsCache ?: run {
        val graph = mutableMapOf<UniqueRoom, MutableSet<UniqueRoom>>()

        for (tile in MapState.dungeonList) {
            val door = tile as? DoorTile ?: continue
            val rooms = door.roomTiles.mapNotNull { it.uniqueRoom }
            if (rooms.size != 2 || rooms[0] == rooms[1]) continue
            graph.getOrPut(rooms[0], ::mutableSetOf).add(rooms[1])
            graph.getOrPut(rooms[1], ::mutableSetOf).add(rooms[0])
        }

        graph.also { splitsCache = it }
    }
}
