package org.blackaddons.cheat.features.impl.cheats.autoMask

import org.blackaddons.api.Platform
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.ServerTickEvent
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.event.skyblock.InvincibilityProcEvent
import org.blackaddons.api.feature.Categories
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.gui.task.ContainerTask
import org.blackaddons.api.gui.task.ContainerTaskResult
import org.blackaddons.api.gui.task.ContainerTaskSettings
import org.blackaddons.api.gui.task.ContainerTasks
import org.blackaddons.api.hud.AnchorX
import org.blackaddons.api.hud.AnchorY
import org.blackaddons.api.player.InventorySlots
import org.blackaddons.api.skyblock.LocationUtils
import org.blackaddons.api.skyblock.dungeon.Dungeon
import org.blackaddons.api.skyblock.dungeon.F7Phase
import org.blackaddons.api.skyblock.dungeon.TerminalMenu
import org.blackaddons.api.skyblock.player.Invincibility
import org.blackaddons.api.skyblock.player.InvincibilityItem
import org.blackaddons.api.skyblock.player.Pet
import org.blackaddons.api.skyblock.player.Pets
import org.blackaddons.api.skyblock.player.SkyblockMenus
import org.blackaddons.api.ui.toast.ToastKind
import org.blackaddons.api.ui.toast.Toasts
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.ServerClock
import org.blackaddons.api.util.inWholeTicks
import org.blackaddons.cheat.features.impl.cheats.autoMask.AutoMask.PHOENIX_WATCH_TICKS
import org.blackaddons.cheat.features.impl.cheats.autoMask.AutoMask.priority
import kotlin.time.Duration.Companion.seconds

private val tr = I18n.scope("blackaddons.auto_mask")

/**
 * After a mask or Phoenix saves the player, equips the next ready one in [priority] order: masks through the equipment
 * menu, and Phoenix by a rod swap (an Autopet rule) or through the pets menu. Phoenix only waits [PHOENIX_WATCH_TICKS]
 * for its proc. After that, or once nothing is left, the previous pet comes back.
 */
object AutoMask : Feature(
    description = "Swaps to your next invincibility item after one saves you.",
    name = "Auto Mask",
) {

    override val section = Categories.Cheats.Dungeons

    private val spiritMask by boolean("Spirit Mask", default = true)
        .withDescription("Equip the Spirit Mask after a proc.")
    private val bonzoMask by boolean("Bonzo's Mask", default = true)
        .withDescription("Equip Bonzo's Mask after a proc.")
    private val phoenixPet by boolean("Phoenix Pet")
        .withDescription("Swap to the Phoenix pet after a proc.")
    private val priority by order("Priority", listOf(SPIRIT, PHOENIX, BONZO))
        .withDescription("Drag the items into the order to swap to them; the first ready one wins.")
    private val swapDelay by number("Swap Delay", 0.0, 0.0, 40.0, 1.0, "ticks")
        .withDescription("Server ticks to wait after a proc before swapping.")
    private val phoenixMethod by mode("Phoenix Swap", ROD_SWAP, listOf(ROD_SWAP, PET_MENU))
        .withDescription("Rod Swap casts a hotbar fishing rod for an Autopet rule; Pet Menu summons Phoenix from the pets menu.")
        .showIf { phoenixPet.value }
    private val bossOnly by boolean("Boss Only")
        .withDescription("Only swap in the boss room.")
    private val phase3Only by boolean("Phase 3 Only")
        .withDescription("Only swap during Goldor's phase.")
    private val menus = ContainerTaskSettings(this)

    @Suppress("unused")
    private val progress = hudElement(
        name = "Auto Mask",
        enabled = { menus.progressShown },
        shouldDraw = { task != null },
        defaultAnchorX = AnchorX.CENTER,
        defaultAnchorY = AnchorY.MIDDLE,
        defaultOffsetY = PROGRESS_OFFSET_Y,
    ) { example -> ContainerTaskSettings.progressCard(task, example) }

    private sealed interface Swap {
        data class Mask(val item: InvincibilityItem) : Swap
        data object Phoenix : Swap
    }

    private class Revert(val message: String, val swapId: Int, val watchId: Int)

    private class Watch(val until: ServerClock.Deadline, val id: Int)

    internal var clock = ServerClock()
    private val rodSwap = RodSwap(this)
    private var task: ContainerTask? = null
    private var pending: Swap? = null
    private var pendingAt: ServerClock.Deadline? = null
    private var revert: Revert? = null
    private var previousPet: Pet? = null
    private var phoenixWatch: Watch? = null
    private var phoenixSwapId = 0
    private var phoenixWatchId = 0
    private var swapRunning = false

    private val swapping: Boolean get() = pending != null || swapRunning

    override fun init() {
        register<InvincibilityProcEvent> { onProc(event.item) }
        register<ServerTickEvent> {
            clock.tick()
            checkPhoenixWatch()
        }
        register<ClientTickEvent.Pre> {
            rodSwap.tick()
            drive()
        }
        register<WorldChangeEvent> { reset() }
    }

    override fun onDisable() {
        super.onDisable()
        reset()
    }

    private fun onProc(proc: InvincibilityItem) {
        if (bossOnly.value && !LocationUtils.inBoss) return
        if (phase3Only.value && Dungeon.run.bossPhase != F7Phase.GOLDOR) return
        if (proc == InvincibilityItem.PHOENIX) phoenixWatchId++ else if (isDead() || swapping) return

        val next = order().firstOrNull(::canSwapTo)
        when (next) {
            null -> requestRevert(tr("none_left", "Auto Mask: no invincibility left, back to your previous pet."))
            InvincibilityItem.PHOENIX -> schedule(Swap.Phoenix)
            else -> schedule(Swap.Mask(next))
        }
        if (proc == InvincibilityItem.PHOENIX) startPhoenixWatch()
    }

    private fun order(): List<InvincibilityItem> = priority.value.map { InvincibilityItem.valueOf(it.uppercase()) }

    private fun canSwapTo(item: InvincibilityItem): Boolean {
        if (!Invincibility.isReady(item)) return false
        return when (item) {
            InvincibilityItem.SPIRIT -> spiritMask.value && maskAvailable(item)
            InvincibilityItem.BONZO -> bonzoMask.value && maskAvailable(item)
            InvincibilityItem.PHOENIX -> phoenixPet.value && !phoenixOut()
        }
    }

    private fun maskAvailable(item: InvincibilityItem): Boolean {
        val mask = item.mask ?: return false
        return Invincibility.wornMask != item && (0 until InventorySlots.STORAGE).any { Platform.player.inventoryItem(it)?.let(mask) == true }
    }

    private fun schedule(swap: Swap) {
        if (swapping) return
        pending = swap
        pendingAt = clock.after(swapDelay.value.toInt())
    }

    private fun requestRevert(message: String) {
        if (previousPet == null) return
        revert = Revert(message, phoenixSwapId, phoenixWatchId)
    }

    private fun drive() {
        if (task != null || rodSwap.isActive) return
        val swap = pending
        if (swap != null) {
            if (isDead()) return clearPending()
            if (pendingAt?.let(clock::passed) != true || !menusFree()) return
            clearPending()
            swapRunning = true
            when (swap) {
                is Swap.Mask -> equipMask(swap.item)
                Swap.Phoenix -> swapToPhoenix()
            }
            return
        }
        val back = revert ?: return
        if (back.swapId != phoenixSwapId || back.watchId != phoenixWatchId || isDead() || !phoenixOut()) {
            revert = null
            return
        }
        if (!menusFree()) return
        revert = null
        Toasts.show(back.message)
        val pet = previousPet ?: return
        if (phoenixMethod.value == ROD_SWAP) castRod {} else summon(pet)
    }

    private fun menusFree(): Boolean = !TerminalMenu.isOpen && ContainerTasks.active == null

    private fun equipMask(item: InvincibilityItem) {
        val mask = item.mask ?: return run { swapRunning = false }
        Toasts.show(tr("equipping", "Auto Mask: equipping %s.", item.displayName))
        start(SkyblockMenus.equip(item.displayName, menus.options(), onFinished = { result ->
            task = null
            swapRunning = false
            if (result.failed) Toasts.show(tr("equip_failed", "Auto Mask: could not equip %s.", item.displayName), kind = ToastKind.ERROR)
        }, item = mask))
    }

    private fun swapToPhoenix() {
        val swapId = ++phoenixSwapId
        Pets.current?.takeUnless { it.matches(PHOENIX) }?.let { previousPet = it }
        if (phoenixMethod.value == ROD_SWAP) {
            castRod { cast ->
                swapRunning = false
                if (cast && swapId == phoenixSwapId) startPhoenixWatch()
            }
            return
        }
        Toasts.show(tr("summoning", "Auto Mask: swapping to Phoenix."))
        start(SkyblockMenus.summonPet(PHOENIX, menus.options(), onFinished = { result ->
            task = null
            swapRunning = false
            if (result == ContainerTaskResult.Success && swapId == phoenixSwapId) startPhoenixWatch()
            if (result.failed) Toasts.show(tr("summon_failed", "Auto Mask: could not summon %s.", PHOENIX), kind = ToastKind.ERROR)
        }) { it.matches(PHOENIX) })
    }

    private fun summon(pet: Pet) {
        start(SkyblockMenus.summonPet(pet.name, menus.options(), onFinished = { result ->
            task = null
            if (result.failed) Toasts.show(tr("summon_failed", "Auto Mask: could not summon %s.", pet.name), kind = ToastKind.ERROR)
        }) { it.sameAs(pet) })
    }

    private fun castRod(onDone: (Boolean) -> Unit) {
        Toasts.show(tr("rod_swap", "Auto Mask: rod swapping."))
        rodSwap.start { failure ->
            onDone(failure == null)
            when (failure) {
                null -> Unit
                RodSwap.Failure.NO_ROD -> Toasts.show(tr("no_rod", "Auto Mask: no fishing rod in your hotbar."), kind = ToastKind.ERROR)
                RodSwap.Failure.NOT_SENT -> Toasts.show(tr("rod_failed", "Auto Mask: the rod cast did not go through."), kind = ToastKind.ERROR)
            }
        }
    }

    private fun start(next: ContainerTask) {
        task = next
        next.run()
    }

    private fun startPhoenixWatch() {
        phoenixWatch = Watch(clock.after(PHOENIX_WATCH_TICKS), ++phoenixWatchId)
    }

    private fun checkPhoenixWatch() {
        val watch = phoenixWatch ?: return
        if (watch.id != phoenixWatchId) {
            phoenixWatch = null
            return
        }
        if (!clock.passed(watch.until)) return
        phoenixWatch = null
        if (!isDead() && phoenixOut()) requestRevert(tr("phoenix_timeout", "Auto Mask: Phoenix did not proc within 5s, back to your previous pet."))
    }

    private val ContainerTaskResult.failed: Boolean
        get() = this != ContainerTaskResult.Success && this != ContainerTaskResult.Cancelled

    private fun phoenixOut(): Boolean = Pets.isSummoned(PHOENIX)

    private fun isDead(): Boolean = Dungeon.team.self?.isDead == true

    private fun clearPending() {
        pending = null
        pendingAt = null
    }

    private fun reset() {
        task?.cancel()
        task = null
        rodSwap.cancel()
        clearPending()
        swapRunning = false
        revert = null
        previousPet = null
        phoenixWatch = null
        phoenixSwapId++
        phoenixWatchId++
    }

    private const val SPIRIT = "Spirit"
    private const val BONZO = "Bonzo"
    private const val ROD_SWAP = "Rod Swap"
    private const val PET_MENU = "Pet Menu"
    private const val PHOENIX = "Phoenix"
    private val PHOENIX_WATCH_TICKS = 5.seconds.inWholeTicks.toInt()
    private const val PROGRESS_OFFSET_Y = 35f
}
