package org.blackaddons.cheat.features.impl.cheats.lightsDevice

import org.blackaddons.api.Platform
import org.blackaddons.api.world.BlockPos3

/** F7/M7 phase 3 lights device: 5x4 levers, all start off. The answer is always the four corners and the middle two. */
internal object LightsDevice {

    private const val WALL_Z = 142

    val levers: Set<BlockPos3> = (58..62).flatMap { x -> (133..136).map { y -> BlockPos3(x, y, WALL_Z) } }.toSet()

    val solution: Set<BlockPos3> = setOf(
        BlockPos3(58, 136, WALL_Z), BlockPos3(62, 136, WALL_Z),
        BlockPos3(58, 133, WALL_Z), BlockPos3(62, 133, WALL_Z),
        BlockPos3(60, 135, WALL_Z), BlockPos3(60, 134, WALL_Z),
    )

    val wrong: Set<BlockPos3> = levers - solution

    /** Non-levers count as done. */
    fun needsFlip(lever: BlockPos3): Boolean =
        lever in solution && Platform.world.blockProperty(lever.x, lever.y, lever.z, "powered") == "false"
}
