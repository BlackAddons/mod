package org.blackaddons.core.dungeon.map

/**
 * Room-local columns to world columns and back. Room data, FME rules and routes are written room-local, from the clay
 * anchor in the room's default rotation.
 */
internal data class RoomTransform(val anchorX: Int, val anchorZ: Int, val rotation: Int) {

    fun toLocal(worldX: Int, worldZ: Int): Pair<Int, Int> {
        val dx = worldX - anchorX
        val dz = worldZ - anchorZ
        return when (normalizedRotation) {
            90 -> dz to -dx
            180 -> -dx to -dz
            270 -> -dz to dx
            else -> dx to dz
        }
    }

    fun toWorld(localX: Int, localZ: Int): Pair<Int, Int> = when (normalizedRotation) {
        90 -> anchorX - localZ to anchorZ + localX
        180 -> anchorX - localX to anchorZ - localZ
        270 -> anchorX + localZ to anchorZ - localX
        else -> anchorX + localX to anchorZ + localZ
    }

    val quarterTurns: Int get() = normalizedRotation / 90

    private val normalizedRotation: Int get() = ((rotation % 360) + 360) % 360
}
