package org.blackaddons.core.features.impl.dungeons.simonSays

import org.blackaddons.api.Platform
import org.blackaddons.api.world.BlockHitboxes
import org.blackaddons.api.world.BlockPos3
import org.blackaddons.api.world.Box3d
import org.blackaddons.api.world.Vec3d

/**
 * F7 P3 Simon Says geometry and first line. The solver and the autosolver share it, so they agree. Coordinates are
 * fixed.
 */
object SimonSaysDevice {

    val GOLDOR_INTRO = Regex("""^\[BOSS] Goldor: Who dares trespass into my domain\?$""")

    val START_BUTTON = BlockPos3(110, 121, 91)

    /** Present while a round is running. */
    val BUTTON_CHECK = BlockPos3(110, 120, 92)

    const val SEA_LANTERN = "minecraft:sea_lantern"
    const val STONE_BUTTON = "minecraft:stone_button"
    const val OBSIDIAN = "minecraft:obsidian"
    const val AIR = "minecraft:air"

    val lanterns: List<BlockPos3> = buildList {
        for (dy in 0..3) for (dz in 0..3) add(BlockPos3(LANTERN_X, 120 + dy, 92 + dz))
    }

    val buttons: List<BlockPos3> = lanterns.map(::buttonFor)

    fun buttonFor(lantern: BlockPos3) = BlockPos3(lantern.x - 1, lantern.y, lantern.z)

    /**
     * Random point in the middle [spread] of the button's real (vanilla) box. A stone button is a thin plate, so aiming
     * at the block center misses. Uses the block center if the chunk isn't loaded.
     */
    fun aimPoint(button: BlockPos3, spread: Double): Vec3d =
        BlockHitboxes.vanilla { Platform.world.blockOutlineBoxes(button.x, button.y, button.z) }
            .firstOrNull()?.randomPoint(spread)
            ?: Vec3d(button.x + PLATE_CENTRE_X, button.y + 0.5, button.z + 0.5)

    const val PLATE_CENTRE_X = 0.9375

    /** Plate of an unpressed button, to draw where the device removed the button. */
    fun plateBox(button: BlockPos3) = Box3d(
        Vec3d(button.x + 0.875, button.y + 0.375, button.z + 0.3125),
        Vec3d(button.x + 1.0, button.y + 0.625, button.z + 0.6875),
    )

    private const val LANTERN_X = 111
}
