package org.blackaddons.cheat.features.impl.cheats

import org.blackaddons.api.Platform
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.feature.Categories
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.player.ClientAction
import org.blackaddons.api.player.Hotbar
import org.blackaddons.api.player.HotbarBorrow
import org.blackaddons.api.skyblock.LocationUtils
import org.blackaddons.api.skyblock.SkyblockItem
import org.blackaddons.api.util.TickTimer
import org.blackaddons.api.world.BlockPos3
import org.blackaddons.cheat.features.impl.cheats.AutoTNT.target
import org.blackaddons.cheat.features.impl.cheats.AutoTNT.useDelay

/**
 * Blows cracked Catacombs walls with Superboom TNT while u look at them: swap, click, swap back, never turning the
 * view. Runs on [ClientTickEvent.Pre]: `gameMode.tick()` flushes the slot before `handleKeybinds()` sends the click, so
 * swap-then-click needs no delay, but swapping back in the same pass would send the old item. The other delays are just
 * there to look human.
 */
object AutoTNT : Feature(
    description = "Blows up cracked Catacombs walls with Superboom TNT while you look at them.",
    name = "Auto TNT",
) {

    override val section = Categories.Cheats.Dungeons

    private val button by mode("Button", default = "Right", modes = listOf("Right", "Left"))
        .withDescription("Which click blows the wall. Both work; right click is the one the item's own ability is written for.")

    private val useDelay by number("Use Delay", default = 2.0, min = 0.0, max = 20.0, step = 1.0, unit = "ticks")
        .withDescription("Wait between taking the TNT and clicking. Nothing needs it to work - it is here so the swap and the click are not the same instant every time.")
        .safeRange(2.0..20.0, "BANNABLE", "Clicking in the same tick you swapped is a gap no hand produces")

    private val delayJitter by number("Delay Jitter", default = 2.0, min = 0.0, max = 10.0, step = 1.0, unit = "ticks")
        .withDescription("Up to this many extra ticks are added to each wait, drawn fresh every time.")
        .safeRange(1.0..10.0, "UNSAFE", "With no jitter every boom sits the identical number of ticks after the swap, wall after wall")

    private val reach by number("Reach", default = 3.3, min = 1.0, max = 5.0, step = 0.1, unit = "blocks")
        .withDescription("How close the wall has to be before it fires. Measured from where you stand to the block's own outline, not to the middle of the space it sits in.")

    private val retries by number("Retries", default = 1.0, min = 0.0, max = 3.0, step = 1.0)
        .withDescription("Extra clicks if the wall is still standing afterwards. A wall that broke stops being a target, so these cost nothing in the normal case.")

    private val swapBack by boolean("Swap Back", default = true)
        .withDescription("Put the slot you were holding back once the wall is dealt with. Off leaves you on the TNT.")
        .group("Swap Back")

    private val swapBackDelay by number("Swap Back Delay", default = 3.0, min = 1.0, max = 20.0, step = 1.0, unit = "ticks")
        .withDescription("How long to keep the TNT out after clicking. One tick is the mechanical floor; more of them is what a hand looks like.")
        .showIf { swapBack.value }
        .group("Swap Back")

    /** `smooth_stone_slab`: Hypixel builds some cracks out of the pre-flattening double stone slab. */
    private val CRACK_BLOCKS = setOf(
        "minecraft:cracked_stone_bricks",
        "minecraft:smooth_stone_slab",
    )

    /** Only blowable in the F7/M7 boss. */
    private const val BARRIER = "minecraft:barrier"

    private val BOOM_TNT = SkyblockItem(setOf("SUPERBOOM_TNT", "INFINITE_SUPERBOOM_TNT"), listOf("Superboom TNT", "Infinityboom TNT"))

    /**
     * Longer than [useDelay]: a boomed wall stays on the client until the break arrives, and firing again just wastes
     * TNT.
     */
    private const val RETRY_DELAY_TICKS = 10

    /** Kept after the boom so we don't boom the same wall again. */
    private var target: BlockPos3? = null

    private var shotsLeft = 0

    private var tntSlot: Int? = null

    private val cooldown = TickTimer()
    private val borrow = HotbarBorrow()

    override fun init() {
        register<ClientTickEvent.Pre> { onTick() }
    }

    override fun onDisable() {
        super.onDisable()
        // Turning off mid-boom must not leave the player on a slot they didn't pick, or carry state into next time.
        borrow.release()
        forgetTarget()
        cooldown.clear()
        tntSlot = null
    }

    private fun onTick() {
        cooldown.tick()

        /*
         * The player changed slots: drop the borrow and this wall, but keep it as target so it doesn't count as new.
         * Checked first, since after a warp the slot is theirs too.
         */
        if (borrow.isActive && Platform.interaction.selectedSlot != borrow.slot) {
            borrow.forget()
            shotsLeft = 0
            return
        }

        if (!canRun()) {
            handBack()
            forgetTarget()
            return
        }

        val wall = wallInClickTarget()
        if (wall != target) {
            target = wall
            shotsLeft = 0
            // Only a newly targeted wall arms a countdown. Looking away would cut the hand-back wait.
            wall?.let {
                shotsLeft = 1 + retries.value.toInt()
                cooldown.arm(useDelay.value.toInt(), delayJitter.value.toInt())
            }
        }
        if (wall == null || shotsLeft <= 0) {
            handBack()
            return
        }

        val slot = Hotbar.find(preferred = tntSlot, match = BOOM_TNT)
        tntSlot = slot
        if (slot == null) {
            handBack()
            return
        }

        borrow.take(slot, restore = swapBack.value)
        if (!cooldown.ready) return

        fire()
        shotsLeft--
        cooldown.arm(
            if (shotsLeft > 0) RETRY_DELAY_TICKS else swapBackDelay.value.toInt(),
            delayJitter.value.toInt(),
        )
    }

    private fun canRun(): Boolean =
        Platform.player.isInGame && !Platform.screenHost.anyScreenOpen && LocationUtils.inDungeon

    /** The wall the click would hit (follows silent rotation), or null. A mob in the way means no target. */
    private fun wallInClickTarget(): BlockPos3? {
        if (Platform.interaction.clickTargetEntity) return null
        val pos = Platform.interaction.clickTargetBlock ?: return null
        val blockId = Platform.world.blockIdAt(pos.x, pos.y, pos.z)
        if (!isTarget(blockId) || !inWall(pos, blockId)) return null
        val aim = Platform.world.blockAimPoint(pos.x, pos.y, pos.z) ?: pos.center()
        val limit = reach.value
        return pos.takeIf { aim.distanceSqr(Platform.player.pos) <= limit * limit }
    }

    private fun isTarget(blockId: String?): Boolean = when (blockId) {
        in CRACK_BLOCKS -> true
        BARRIER -> LocationUtils.hasF7Boss && LocationUtils.inBoss
        else -> false
    }

    /** A lone crack, or the end of a line of them, is just decoration: at least half the touching blocks must match. */
    private fun inWall(pos: BlockPos3, blockId: String?): Boolean {
        val neighbours = pos.faceNeighbours()
        return neighbours.count { Platform.world.blockIdAt(it.x, it.y, it.z) == blockId } * 2 >= neighbours.size
    }

    private fun fire() {
        Platform.interaction.submit(if (button.value == "Left") ClientAction.Attack else ClientAction.Use, owner = this)
    }

    /**
     * Swaps back once the server has seen the TNT. Leaves [target] alone: clearing it made a handled wall look new next
     * tick, so it got boomed forever.
     */
    private fun handBack() {
        if (!borrow.isActive || !cooldown.ready) return
        borrow.release()
    }

    private fun forgetTarget() {
        target = null
        shotsLeft = 0
    }
}
