package org.blackaddons.core.dungeon.map

import org.blackaddons.api.Platform
import org.blackaddons.api.skyblock.LocationUtils
import org.blackaddons.api.skyblock.dungeon.MapOverrides
import org.blackaddons.api.skyblock.dungeon.RoomState
import org.blackaddons.api.skyblock.dungeon.RoomType

/** Shows entered rooms and their neighbors before the map item does. Only when `MapOverride.keepsOptimisticRooms`. */
internal object EnteredRoomReveal {

    private var enteredRoom: UniqueRoom? = null

    fun tick() {
        if (!LocationUtils.inDungeonRooms || !MapOverrides.keepsOptimisticRooms) {
            enteredRoom = null
            return
        }
        val room = MapState.getRoomAt(Platform.player.x, Platform.player.z)
        if (room === enteredRoom) return
        enteredRoom = room
        room?.let(::reveal)
    }

    fun reset() {
        enteredRoom = null
    }

    fun reveal(room: UniqueRoom) {
        if (
            room.data.type == RoomType.ENTRANCE ||
            (room.mainRoom.state != RoomState.UNDISCOVERED && room.mainRoom.state != RoomState.UNOPENED)
        ) return

        room.tiles.forEach { it.state = RoomState.DISCOVERED }

        val adjacentRooms = linkedMapOf<UniqueRoom, RoomTile>()
        for (door in MapState.dungeonList.filterIsInstance<DoorTile>()) {
            val doorRooms = door.roomTiles
            if (doorRooms.none { it.uniqueRoom === room }) continue

            for (neighbor in doorRooms) {
                val adjacentRoom = neighbor.uniqueRoom
                if (adjacentRoom === room) continue
                if (adjacentRoom != null) {
                    if (adjacentRoom.tiles.any { it.state != RoomState.UNDISCOVERED }) continue
                    adjacentRooms.putIfAbsent(adjacentRoom, neighbor)
                } else if (neighbor.state == RoomState.UNDISCOVERED) {
                    recordVisibleEntry(neighbor)
                    neighbor.state = RoomState.UNOPENED
                }
            }
        }
        adjacentRooms.forEach { (adjacent, entry) ->
            recordVisibleEntry(entry)
            adjacent.tiles.forEach { it.state = RoomState.UNOPENED }
        }
        RoomPrediction.refresh()
    }

    private fun recordVisibleEntry(tile: RoomTile) {
        val (gridZ, gridX) = tile.getGridPos()
        MapState.recordVisibleRoomEntry(gridX, gridZ)
    }
}
