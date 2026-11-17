package org.blackaddons.cheat.features.impl.cheats

import org.blackaddons.api.Platform
import org.blackaddons.api.event.EventPriority
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.event.input.MovementInputEvent
import org.blackaddons.api.event.world.BlockInteractEvent
import org.blackaddons.api.event.world.ContainerOpenEvent
import org.blackaddons.api.feature.Categories
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.player.Lease
import org.blackaddons.api.player.MoveKey
import org.blackaddons.api.skyblock.LocationUtils
import org.blackaddons.api.skyblock.dungeon.Dungeon
import org.blackaddons.api.skyblock.dungeon.SecretChests
import org.blackaddons.api.world.BlockPos3
import kotlin.random.Random

object AutoCloseChest : Feature(
    description = "Keeps dungeon secret chests from opening their window.",
    name = "Auto Close Chest",
) {
    internal const val SNEAK = "Sneak"
    internal const val CLOSE_ON_OPEN = "Close On Open"

    override val section = Categories.Cheats.Dungeons

    internal val mode by mode("Mode", default = CLOSE_ON_OPEN, modes = listOf(SNEAK, CLOSE_ON_OPEN))
        .withDescription("Sneak: sneak as you click a chest, so it counts without opening.\nClose On Open: close the chest window the moment the server opens it.")
    internal val minSneakTicks by number("Min Sneak Ticks", default = 2.0, min = 1.0, max = 20.0, unit = "ticks")
        .withDescription("Shortest time sneak stays held after the click.")
        .showIf { mode.value == SNEAK }
    internal val maxSneakTicks by number("Max Sneak Ticks", default = 4.0, min = 1.0, max = 20.0, unit = "ticks")
        .withDescription("Longest time sneak stays held after the click. Each click picks a random length between the two.")
        .showIf { mode.value == SNEAK }

    private var sneakTicks = 0
    private var sneak: Lease? = null
    private var lastUsed: BlockPos3? = null

    override fun init() {
        register<BlockInteractEvent.Use>(EventPriority.LOWEST) { onUse(BlockPos3(event.x, event.y, event.z)) }
        register<MovementInputEvent> {
            if (sneakTicks > 0) sneakTicks-- else releaseSneak()
        }
        register<ContainerOpenEvent> { if (shouldClose(event)) event.cancelled = true }
        register<WorldChangeEvent> { reset() }
    }

    override fun onDisable() {
        super.onDisable()
        reset()
    }

    private fun reset() {
        releaseSneak()
        lastUsed = null
    }

    private fun releaseSneak() {
        sneakTicks = 0
        sneak?.release()
        sneak = null
    }

    private fun onUse(pos: BlockPos3) {
        lastUsed = pos
        if (mode.value != SNEAK || sneakTicks > 0 || Platform.player.isSneaking || !isSecretChest(pos)) return
        Platform.movement.sneakNow()
        sneak = Platform.movement.hold(MoveKey.SNEAK, this)
        val min = minSneakTicks.value.toInt()
        sneakTicks = Random.nextInt(min, maxOf(min, maxSneakTicks.value.toInt()) + 1)
    }

    private fun isSecretChest(pos: BlockPos3): Boolean =
        LocationUtils.inDungeonRooms &&
            SecretChests.isChestBlock(Platform.world.blockIdAt(pos.x, pos.y, pos.z)) &&
            !Dungeon.layout.isMimicChest(pos)

    private fun shouldClose(event: ContainerOpenEvent): Boolean =
        mode.value == CLOSE_ON_OPEN && LocationUtils.inDungeonRooms &&
            SecretChests.isChestWindow(event.title, event.menuType) &&
            lastUsed?.let(Dungeon.layout::isMimicChest) != true
}
