package org.blackaddons.cheat.features.impl.cheats

import org.blackaddons.api.Platform
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.world.FishingRodEvent
import org.blackaddons.api.feature.Categories
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.player.ClientAction
import org.blackaddons.api.player.Hotbar
import org.blackaddons.api.player.HotbarBorrow
import org.blackaddons.api.skyblock.LocationUtils
import org.blackaddons.api.skyblock.SkyblockItem
import org.blackaddons.api.skyblock.player.SkyblockStats
import org.blackaddons.api.util.TickTimer
import org.blackaddons.api.util.secondsToTicks
import org.blackaddons.api.util.stripFormatting

/**
 * Casts the Fire Veil Wand on a timer: swap, right click, swap back, the same borrow flow as [AutoTNT]. Runs on
 * [ClientTickEvent.Pre] for the same reason: the swap is flushed before the click goes out.
 */
object AutoFireVeil : Feature(
    description = "Casts Fire Veil every few seconds while you have the wand in your hotbar and the mana for it.",
    name = "Auto Fire Veil",
) {

    override val section = Categories.Cheats.General

    private val castInterval by number("Interval", default = 5.0, min = 1.0, max = 60.0, step = 0.5, unit = "s")
        .withDescription("Time between casts. Fire Veil lasts 5 seconds and has a 5 second cooldown; Mage in a dungeon casts it sooner, so just set it to whatever you feel like tbh.")

    private val swapBack by boolean("Swap Back", default = true)
        .withDescription("Swap back to your previous hotbar slot after cast.")
        .group("Swap Back")

    private val minSwapDelay by number("Min Swap Delay", default = 1.0, min = 1.0, max = 10.0, step = 1.0, unit = "ticks")
        .withDescription("Min Swap Delay for swappping.")

    private val maxSwapDelay by number("Max Swap Delay", default = 2.0, min = 1.0, max = 10.0, step = 1.0, unit = "ticks")
        .withDescription("Max Swap Delay for swappping.")

    private val waitForRod by boolean("Wait For Rod", default = true)
        .withDescription("Wait when your fishing line is out.")

    private val FIRE_VEIL_WAND = SkyblockItem("FIRE_VEIL_WAND", "Fire Veil Wand")

    private const val JCNLK_TICKS = 7 // JC MENTIONED!!!!

    private const val ROD_SETTLE_TICKS = 10

    private var wandSlot: Int? = null

    private var casting = false

    private val nextCast = TickTimer()
    private val swap = TickTimer()
    private val rodSettle = TickTimer()
    private val borrow = HotbarBorrow()

    override fun init() {
        register<ClientTickEvent.Pre> { onTick() }
        register<FishingRodEvent.Reel> { rodSettle.arm(ROD_SETTLE_TICKS) }
    }

    override fun onDisable() {
        super.onDisable()
        borrow.release()
        casting = false
        nextCast.clear()
        swap.clear()
        rodSettle.clear()
        wandSlot = null
    }

    private fun onTick() {
        nextCast.tick()
        swap.tick()
        rodSettle.tick()

        if (borrow.isActive && Platform.interaction.selectedSlot != borrow.slot) {
            borrow.forget()
            casting = false
            return
        }

        if (!canRun()) {
            casting = false
            handBack()
            return
        }

        if (!casting) {
            handBack()
            if (!nextCast.ready || borrow.isActive || rodBusy()) return
            val slot = Hotbar.find(preferred = wandSlot, match = FIRE_VEIL_WAND) ?: return
            wandSlot = slot
            if (!hasManaFor(slot)) return
            borrow.take(slot, restore = swapBack.value)
            armSwap()
            casting = true
            return
        }

        if (!swap.ready) return
        Platform.interaction.submit(ClientAction.UseItem, owner = this)
        casting = false
        nextCast.arm(castInterval.value.secondsToTicks + JCNLK_TICKS)
        armSwap()
    }

    private fun armSwap() {
        val min = minSwapDelay.value.toInt()
        swap.arm(min, maxSwapDelay.value.toInt() - min)
    }

    private fun canRun(): Boolean =
        Platform.player.isInGame && !Platform.screenHost.anyScreenOpen && LocationUtils.inSkyblock

    private fun rodBusy(): Boolean = waitForRod.value && (Platform.player.isFishing || !rodSettle.ready)

    private fun hasManaFor(slot: Int): Boolean {
        val stats = SkyblockStats.current
        val mana = stats.mana ?: return false
        val cost = manaCost(Platform.player.inventoryItem(slot)?.loreLines.orEmpty()) ?: return true
        return mana >= cost
    }

    private fun handBack() {
        if (!borrow.isActive || !swap.ready) return
        borrow.release()
    }
}

private val MANA_COST = Regex("""Mana Cost: ([\d,]+)""")

internal fun manaCost(lore: List<String>): Int? =
    lore.firstNotNullOfOrNull { MANA_COST.find(it.stripFormatting()) }?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
