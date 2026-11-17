package org.blackaddons.cheat.features.impl.cheats.arrowAlign

import org.blackaddons.api.world.BlockPos3
import org.blackaddons.api.world.Box3d
import org.blackaddons.api.world.Vec3d

/**
 * F7/M7 phase 3 arrow align: a 5x5 item-frame wall at x = -2. Which frames hold arrows tells us the layout, and each
 * layout has one answer (turns per arrow, in eighths). Layouts from NoammAddons' ArrowAlign (CC0-1.0).
 */
internal object ArrowAlignBoard {

    const val SIZE = 25
    const val TURNS = 8
    const val NO_ARROW = -1

    private const val WALL_X = -2
    private const val MIN_Y = 120
    private const val MIN_Z = 75
    private const val SIDE = 5

    val area = Box3d(
        Vec3d(WALL_X.toDouble(), MIN_Y.toDouble(), MIN_Z.toDouble()),
        Vec3d(WALL_X + 1.0, (MIN_Y + SIDE).toDouble(), (MIN_Z + SIDE).toDouble()),
    )

    // index = (y - 120) + (z - 75) * 5.
    val layouts: List<IntArray> = listOf(
        intArrayOf(7, 7, -1, -1, -1, 1, -1, -1, -1, -1, 1, 3, 3, 3, 3, -1, -1, -1, -1, 1, -1, -1, -1, 7, 1),
        intArrayOf(-1, -1, 7, 7, 5, -1, 7, 1, -1, 5, -1, -1, -1, -1, -1, -1, 7, 5, -1, 1, -1, -1, 7, 7, 1),
        intArrayOf(7, 7, -1, -1, -1, 1, -1, -1, -1, -1, 1, 3, -1, 7, 5, -1, -1, -1, -1, 5, -1, -1, -1, 3, 3),
        intArrayOf(5, 3, 3, 3, -1, 5, -1, -1, -1, -1, 7, 7, -1, -1, -1, 1, -1, -1, -1, -1, 1, 3, 3, 3, -1),
        intArrayOf(5, 3, 3, 3, 3, 5, -1, -1, -1, 1, 7, 7, -1, -1, 1, -1, -1, -1, -1, 1, -1, 7, 7, 7, 1),
        intArrayOf(7, 7, 7, 7, -1, 1, -1, -1, -1, -1, 1, 3, 3, 3, 3, -1, -1, -1, -1, 1, -1, 7, 7, 7, 1),
        intArrayOf(-1, -1, -1, -1, -1, 1, -1, 1, -1, 1, 1, -1, 1, -1, 1, 1, -1, 1, -1, 1, -1, -1, -1, -1, -1),
        intArrayOf(-1, -1, -1, -1, -1, 1, 3, 3, 3, 3, -1, -1, -1, -1, 1, 7, 7, 7, 7, 1, -1, -1, -1, -1, -1),
        intArrayOf(-1, -1, -1, -1, -1, -1, 1, -1, 1, -1, 7, 1, 7, 1, 3, 1, -1, 1, -1, 1, -1, -1, -1, -1, -1),
    )

    fun index(pos: BlockPos3): Int? {
        if (pos.x != WALL_X || pos.y - MIN_Y !in 0 until SIDE || pos.z - MIN_Z !in 0 until SIDE) return null
        return (pos.y - MIN_Y) + (pos.z - MIN_Z) * SIDE
    }

    /** Null when no known layout matches. We never guess. */
    fun solve(rotations: IntArray): IntArray? {
        if (rotations.size != SIZE) return null
        return layouts.firstOrNull { layout ->
            layout.indices.all { (layout[it] == NO_ARROW) == (rotations[it] == NO_ARROW) }
        }
    }

    fun bottomLeft(indices: Iterable<Int>): Int? = indices.maxByOrNull { (it / SIDE) * SIDE - it % SIDE }

    /** Each click is an eighth. */
    fun clicksNeeded(current: Int, target: Int): Int = Math.floorMod(target - current, TURNS)
}
