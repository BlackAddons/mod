package org.blackaddons.core.dungeon.map

import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.dungeon.DungeonTileScannedEvent
import org.blackaddons.api.skyblock.dungeon.RoomState
import org.blackaddons.api.skyblock.dungeon.RoomType
import org.blackaddons.api.util.equalsIgnoreCase

object MapState {
    const val START_X = -185
    const val START_Z = -185
    const val ROOM_SIZE = 32
    const val HALF_ROOM_SIZE = 15
    const val GRID_SIZE = 11
    const val MAX_GRID_INDEX = GRID_SIZE - 1
    const val GRID_TILE_COUNT = GRID_SIZE * GRID_SIZE

    val gridRange: IntRange = 0..MAX_GRID_INDEX

    /** Even indices are rooms, odd are doors. */
    val dungeonList: Array<Tile> = Array(GRID_TILE_COUNT) { Unknown(0, 0) }
    val uniqueRooms = mutableMapOf<String, UniqueRoom>()
    val mapPlayers = mutableListOf<MapPlayer>()
    private val visibleRoomEntries = mutableSetOf<Pair<Int, Int>>()
    var mimicRoom: UniqueRoom? = null

    var witherDoors = 0
    var cryptCount = 0
    var secretCount = 0

    var startCorner = 5 to 5
    var mapRoomSize = 16
    var coordMultiplier = 0.625
    var calibrated = false
    var mapDataCalibrated = false

    data class Calibration(val roomSize: Int, val startCorner: Pair<Int, Int>, val coordMultiplier: Double)

    val halfRoom: Int get() = mapRoomSize / 2
    val quarterRoom: Int get() = halfRoom / 2

    val bloodRoom: RoomTile?
        get() = uniqueRooms["Blood"]?.mainRoom
            ?: uniqueRooms.values.find { it.data.type == RoomType.BLOOD }?.mainRoom
            ?: dungeonList.filterIsInstance<RoomTile>().find { it.data.type == RoomType.BLOOD }

    fun setBloodRoomState(state: RoomState) {
        val bloodUnique = uniqueRooms["Blood"] ?: uniqueRooms.values.find { it.data.type == RoomType.BLOOD }
        if (bloodUnique != null) {
            bloodUnique.mainRoom.state = state
            for (tile in bloodUnique.tiles) {
                tile.state = state
            }
        } else {
            dungeonList.filterIsInstance<RoomTile>().filter { it.data.type == RoomType.BLOOD }.forEach {
                it.state = state
            }
        }
    }

    fun calibrationFor(floor: Int?, roomSize: Int = defaultRoomSize(floor), entranceStart: Int? = null): Calibration {
        require(roomSize == 16 || roomSize == 18) { "Unsupported dungeon map room size: $roomSize" }
        val startCorner = when (floor) {
            0 -> 22 to 22
            1 -> 22 to 11
            2, 3 -> 11 to 11
            else -> entranceStart?.let { start ->
                val startX = start and 127
                val startZ = start shr 7
                (startX % (roomSize + 4)) to (startZ % (roomSize + 4))
            } ?: 5 to 5
        }
        return Calibration(roomSize, startCorner, (roomSize + 4.0) / ROOM_SIZE)
    }

    fun applyCalibration(calibration: Calibration) {
        mapRoomSize = calibration.roomSize
        startCorner = calibration.startCorner
        coordMultiplier = calibration.coordMultiplier
        calibrated = true
    }

    fun indexOf(gridX: Int, gridZ: Int): Int {
        require(isGridPosition(gridX, gridZ)) { "Dungeon map grid position out of range: $gridX, $gridZ" }
        return gridZ * GRID_SIZE + gridX
    }

    fun tileAt(gridX: Int, gridZ: Int): Tile? =
        if (isGridPosition(gridX, gridZ)) dungeonList[gridZ * GRID_SIZE + gridX] else null

    fun worldAt(gridX: Int, gridZ: Int): Pair<Int, Int> {
        require(isGridPosition(gridX, gridZ)) { "Dungeon map grid position out of range: $gridX, $gridZ" }
        val step = ROOM_SIZE shr 1
        return START_X + gridX * step to START_Z + gridZ * step
    }

    fun adjacentRoomTiles(gridX: Int, gridZ: Int): Pair<RoomTile?, RoomTile?> =
        if (gridZ and 1 == 0) {
            tileAt(gridX - 1, gridZ) as? RoomTile to tileAt(gridX + 1, gridZ) as? RoomTile
        } else {
            tileAt(gridX, gridZ - 1) as? RoomTile to tileAt(gridX, gridZ + 1) as? RoomTile
        }

    fun unidentifiedRoomCells(gridX: Int, gridZ: Int): List<Pair<Int, Int>> {
        val cells = mutableListOf<Pair<Int, Int>>()
        val visited = mutableSetOf(gridX to gridZ)
        val queue = ArrayDeque(listOf(gridX to gridZ))
        while (queue.isNotEmpty()) {
            val (x, z) = queue.removeFirst()
            val tile = tileAt(x, z) as? RoomTile ?: continue
            if (tile.isSeparator || tile.uniqueRoom != null) continue
            cells += x to z
            for ((dx, dz) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                if ((tileAt(x + dx, z + dz) as? RoomTile)?.isSeparator != true) continue
                val next = x + 2 * dx to z + 2 * dz
                if (visited.add(next)) queue += next
            }
        }
        return cells
    }

    fun replaceTile(gridX: Int, gridZ: Int, tile: Tile) {
        dungeonList[indexOf(gridX, gridZ)] = tile
        EventBus.post(DungeonTileScannedEvent(gridX, gridZ))
    }

    fun joinConnectedRooms(connectedCells: (gridX: Int, gridZ: Int) -> List<Pair<Int, Int>>) {
        for (gridX in gridRange) for (gridZ in gridRange) {
            val tile = tileAt(gridX, gridZ) as? RoomTile ?: continue
            if (tile.uniqueRoom != null) continue
            val room = connectedCells(gridX, gridZ)
                .firstNotNullOfOrNull { (x, z) -> (tileAt(x, z) as? RoomTile)?.uniqueRoom } ?: continue
            tile.data = room.data
            tile.addToUnique(gridZ, gridX, room.name)
            EventBus.post(DungeonTileScannedEvent(gridX, gridZ))
        }
    }

    fun reset() {
        RoomPrediction.reset()
        dungeonList.fill(Unknown(0, 0))
        uniqueRooms.clear()
        mapPlayers.clear()
        visibleRoomEntries.clear()
        mimicRoom = null
        witherDoors = 0
        cryptCount = 0
        secretCount = 0
        startCorner = 5 to 5
        mapRoomSize = 16
        coordMultiplier = 0.625
        calibrated = false
        mapDataCalibrated = false
    }

    fun recordVisibleRoomEntry(gridX: Int, gridZ: Int) {
        if (isGridPosition(gridX, gridZ) && gridX and 1 == 0 && gridZ and 1 == 0) {
            visibleRoomEntries += gridX to gridZ
        }
    }

    fun isVisibleRoomEntry(gridX: Int, gridZ: Int): Boolean = gridX to gridZ in visibleRoomEntries

    fun visibleRoomEntries(): Set<Pair<Int, Int>> = visibleRoomEntries.toSet()

    fun player(name: String): MapPlayer? = mapPlayers.firstOrNull { it.name.equalsIgnoreCase(name) }

    fun worldToMap(worldX: Double, worldZ: Double): Pair<Float, Float> {
        val x = ((worldX - START_X + HALF_ROOM_SIZE) * coordMultiplier + startCorner.first).toFloat()
        val z = ((worldZ - START_Z + HALF_ROOM_SIZE) * coordMultiplier + startCorner.second).toFloat()
        return x to z
    }

    fun mapToWorld(mapX: Float, mapZ: Float): Pair<Double, Double> {
        val x = (mapX - startCorner.first) / coordMultiplier + START_X - HALF_ROOM_SIZE
        val z = (mapZ - startCorner.second) / coordMultiplier + START_Z - HALF_ROOM_SIZE
        return x to z
    }

    fun worldToGrid(worldX: Double, worldZ: Double): Pair<Int, Int> {
        val roomIndexX = Math.round((worldX - START_X) / ROOM_SIZE).toInt()
        val roomIndexZ = Math.round((worldZ - START_Z) / ROOM_SIZE).toInt()
        return roomIndexX * 2 to roomIndexZ * 2
    }

    fun getRoomAt(worldX: Double, worldZ: Double): UniqueRoom? {
        val (gx, gz) = worldToGrid(worldX, worldZ)
        return (tileAt(gx, gz) as? RoomTile)?.uniqueRoom
    }

    private fun defaultRoomSize(floor: Int?): Int = if (floor != null && floor <= 3) 18 else 16

    private fun isGridPosition(gridX: Int, gridZ: Int): Boolean = gridX in gridRange && gridZ in gridRange
}
