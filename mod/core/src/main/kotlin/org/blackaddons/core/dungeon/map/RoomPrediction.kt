package org.blackaddons.core.dungeon.map

import org.blackaddons.api.skyblock.dungeon.RoomShape
import org.blackaddons.api.skyblock.dungeon.RoomState
import org.blackaddons.api.skyblock.dungeon.RoomType

object RoomPrediction {
    private data class Position(val column: Int, val row: Int)

    internal data class Layout(val width: Int, val height: Int, val specialColumn: Int, val hasTrap: Boolean)

    private data class OpenedRoom(val type: RoomType, val special: Boolean)

    private var layout: Layout? = null
    private val knownOneByOne = mutableSetOf<Position>()

    /** After the hotbar map is calibrated. */
    fun configure(floor: Int?, startCorner: Pair<Int, Int>) {
        val newLayout = layoutFor(floor, startCorner)
        if (newLayout == layout) return

        layout = newLayout
        knownOneByOne.clear()
    }

    fun refresh() {
        val currentLayout = layout ?: return
        for ((gridX, gridZ) in MapState.visibleRoomEntries()) {
            val position = Position(gridX shr 1, gridZ shr 1)
            if (position.column !in 0 until currentLayout.width || position.row !in 0 until currentLayout.height) continue
            val room = roomAt(position) ?: continue
            if (room.state != RoomState.UNOPENED) continue
            if (room.data.type == RoomType.BLOOD || room.data.type == RoomType.ENTRANCE) continue

            if (position.column == currentLayout.specialColumn || surroundingRoomsSeen(position, currentLayout)) {
                knownOneByOne += position
            }
        }
    }

    fun candidates(gridX: Int, gridZ: Int, puzzleCount: Int): List<RoomType> {
        val currentLayout = layout ?: return emptyList()
        if (puzzleCount <= 0 || gridX and 1 != 0 || gridZ and 1 != 0) return emptyList()

        val position = Position(gridX shr 1, gridZ shr 1)
        val room = roomAt(position) ?: return emptyList()
        if (room.state != RoomState.UNOPENED || !isVisibleEntry(position) || position !in knownOneByOne) {
            return emptyList()
        }
        if (visibleDoorCount(gridX, gridZ) != 1) return emptyList()

        val openedRooms = openedOneByOneRooms(currentLayout)
        val specialColumnRoomCount = knownOneByOne.count { it.column == currentLayout.specialColumn }
            .let { if (currentLayout.specialColumn != -1 && it == 0) 1 else it }
        val discoveredOneByOne = knownOneByOne.size
        val isSpecial = position.column == currentLayout.specialColumn

        return if (isSpecial) {
            specialCandidates(
                currentLayout,
                puzzleCount,
                specialColumnRoomCount,
                discoveredOneByOne,
                openedRooms,
            )
        } else {
            regularCandidates(puzzleCount, specialColumnRoomCount, openedRooms)
        }
    }

    fun reset() {
        layout = null
        knownOneByOne.clear()
    }

    internal fun layoutFor(floor: Int?, startCorner: Pair<Int, Int>): Layout? {
        floor ?: return null
        val (width, height) = when (floor) {
            0 -> 4 to 4
            1 -> 4 to 5
            2, 3 -> 5 to 5
            else -> (5 + if (startCorner.first == 5) 1 else 0) to
                (5 + if (startCorner.second == 5) 1 else 0)
        }
        val specialColumn = when {
            floor == 4 && width == 6 && height == 5 -> 5
            floor in 5..6 && width == 6 && height == 6 -> 5
            else -> -1
        }
        return Layout(width, height, specialColumn, hasTrap = floor > 2)
    }

    private fun specialCandidates(
        currentLayout: Layout,
        puzzleCount: Int,
        specialColumnRoomCount: Int,
        discoveredOneByOne: Int,
        openedRooms: List<OpenedRoom>,
    ): List<RoomType> {
        val discoveredFullSpecialColumn = discoveredFullSpecialColumn(currentLayout, puzzleCount)
        val specialSize = currentLayout.height - discoveredFullSpecialColumn + specialColumnRoomCount
        return when {
            specialSize <= puzzleCount || discoveredOneByOne - specialColumnRoomCount >= 2 ->
                listOf(RoomType.PUZZLE)
            openedRooms.any { it.type == RoomType.TRAP && !it.special } ->
                listOf(RoomType.PUZZLE)
            specialSize == puzzleCount + 1 || discoveredOneByOne - specialColumnRoomCount == 1 ->
                listOf(RoomType.TRAP, RoomType.PUZZLE)
            openedRooms.any { it.type == RoomType.CHAMPION } ->
                listOf(RoomType.CHAMPION, RoomType.TRAP)
            else ->
                listOf(RoomType.CHAMPION, RoomType.TRAP, RoomType.PUZZLE)
        }
    }

    private fun regularCandidates(
        puzzleCount: Int,
        specialColumnRoomCount: Int,
        openedRooms: List<OpenedRoom>,
    ): List<RoomType> {
        val nonSpecialPuzzles = openedRooms.count { it.type == RoomType.PUZZLE && !it.special }
        if (specialColumnRoomCount + nonSpecialPuzzles == puzzleCount) {
            return when {
                openedRooms.any { it.type == RoomType.TRAP } -> listOf(RoomType.CHAMPION)
                openedRooms.any { it.type == RoomType.CHAMPION } -> listOf(RoomType.TRAP)
                else -> listOf(RoomType.CHAMPION, RoomType.TRAP)
            }
        }

        if (specialColumnRoomCount == puzzleCount + 1) return listOf(RoomType.CHAMPION)
        if (openedRooms.count { it.type == RoomType.CHAMPION || it.type == RoomType.TRAP } == 2) {
            return listOf(RoomType.PUZZLE)
        }
        if (openedRooms.size == puzzleCount + 1) {
            if (openedRooms.none { it.type == RoomType.TRAP }) return listOf(RoomType.TRAP)
            if (openedRooms.none { it.type == RoomType.CHAMPION }) return listOf(RoomType.CHAMPION)
        }

        return buildList {
            add(RoomType.PUZZLE)
            if (openedRooms.none { it.type == RoomType.TRAP }) add(RoomType.TRAP)
            if (openedRooms.none { it.type == RoomType.CHAMPION }) add(RoomType.CHAMPION)
        }
    }

    private fun discoveredFullSpecialColumn(currentLayout: Layout, puzzleCount: Int): Int {
        if (knownOneByOne.size == puzzleCount + 2) return currentLayout.height
        if (currentLayout.specialColumn == -1) return 0

        var discovered = currentLayout.height
        for (row in 0 until currentLayout.height) {
            val position = Position(currentLayout.specialColumn - 1, row)
            val room = roomAt(position) ?: continue
            if (position in knownOneByOne) continue
            if (
                room.state == RoomState.UNDISCOVERED ||
                room.state == RoomState.UNOPENED && room.data.type != RoomType.BLOOD
            ) {
                discovered--
            }
        }
        return discovered
    }

    private fun openedOneByOneRooms(currentLayout: Layout): List<OpenedRoom> = buildList {
        for (column in 0 until currentLayout.width) for (row in 0 until currentLayout.height) {
            val position = Position(column, row)
            val room = roomAt(position) ?: continue
            if (room.state == RoomState.UNDISCOVERED || room.state == RoomState.UNOPENED) continue
            if (room.data.type != RoomType.CHAMPION && room.data.type != RoomType.TRAP && room.data.type != RoomType.PUZZLE) {
                continue
            }
            if (position !in knownOneByOne && !canBeOneByOne(room)) continue
            add(OpenedRoom(room.data.type, column == currentLayout.specialColumn))
        }
        /* Entrance and F1-2 have no trap. Counting it as found narrows down the rest. */
        if (!currentLayout.hasTrap) add(OpenedRoom(RoomType.TRAP, special = false))
    }

    private fun surroundingRoomsSeen(position: Position, currentLayout: Layout): Boolean {
        val neighbors = buildList {
            if (position.column + 1 < currentLayout.width && position.column + 1 != currentLayout.specialColumn) {
                add(Position(position.column + 1, position.row))
            }
            if (position.column > 0) add(Position(position.column - 1, position.row))
            if (position.row + 1 < currentLayout.height) add(Position(position.column, position.row + 1))
            if (position.row > 0) add(Position(position.column, position.row - 1))
        }
        return neighbors.all { neighbor ->
            val room = roomAt(neighbor) ?: return@all false
            room.state != RoomState.UNDISCOVERED &&
                (room.state != RoomState.UNOPENED || isVisibleEntry(neighbor))
        }
    }

    private fun isVisibleEntry(position: Position): Boolean =
        MapState.isVisibleRoomEntry(position.column shl 1, position.row shl 1)

    private fun visibleDoorCount(gridX: Int, gridZ: Int): Int = listOf(
        gridX - 1 to gridZ,
        gridX + 1 to gridZ,
        gridX to gridZ - 1,
        gridX to gridZ + 1,
    ).count { (x, z) ->
        val door = MapState.tileAt(x, z) as? DoorTile
        door != null && door.state != RoomState.UNDISCOVERED
    }

    private fun canBeOneByOne(room: RoomTile): Boolean {
        if (room.data.shape != RoomShape.UNKNOWN && room.data.shape != RoomShape.S1x1) return false
        return room.uniqueRoom?.mapCells()?.size?.let { it == 1 } ?: true
    }

    private fun roomAt(position: Position): RoomTile? {
        val gridX = position.column shl 1
        val gridZ = position.row shl 1
        return MapState.tileAt(gridX, gridZ) as? RoomTile
    }
}
