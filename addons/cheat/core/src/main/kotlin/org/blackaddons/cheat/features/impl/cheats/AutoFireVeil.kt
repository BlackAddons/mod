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
import org.blackaddons.api.skyblock.player.SkyblockStats
import org.blackaddons.api.util.TickTimer
import org.blackaddons.api.util.secondsToTicks

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
        .withDescription("Put the slot you were holding back after the cast. Off leaves you on the wand.")
        .group("Swap Back")

    private val FIRE_VEIL_WAND = SkyblockItem("FIRE_VEIL_WAND", "Fire Veil Wand")

    private const val JCNLK_TICKS = 7 // JC MENTIONED!!!!

    private const val SWAP_TICKS = 1
    private const val SWAP_JITTER = 1

    private var wandSlot: Int? = null

    private var casting = false

    private val nextCast = TickTimer()
    private val swap = TickTimer()
    private val borrow = HotbarBorrow()

    override fun init() {
        register<ClientTickEvent.Pre> { onTick() }
    }

    override fun onDisable() {
        super.onDisable()
        borrow.release()
        casting = false
        nextCast.clear()
        swap.clear()
        wandSlot = null
    }

    private fun onTick() {
        nextCast.tick()
        swap.tick()

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
            if (!nextCast.ready || borrow.isActive) return
            val slot = Hotbar.find(preferred = wandSlot, match = FIRE_VEIL_WAND) ?: return
            wandSlot = slot
            if (!hasManaFor(slot)) return
            borrow.take(slot, restore = swapBack.value)
            swap.arm(SWAP_TICKS, SWAP_JITTER)
            casting = true
            return
        }

        if (!swap.ready) return
        Platform.interaction.submit(ClientAction.Use, owner = this)
        casting = false
        nextCast.arm(castInterval.value.secondsToTicks + JCNLK_TICKS)
        swap.arm(SWAP_TICKS, SWAP_JITTER)
    }

    private fun canRun(): Boolean =
        Platform.player.isInGame && !Platform.screenHost.anyScreenOpen && LocationUtils.inSkyblock

    private fun hasManaFor(slot: Int): Boolean {
        val stats = SkyblockStats.current
        val mana = stats.mana ?: return false
        val cost = manaCost(Platform.player.inventoryItem(slot)?.loreLines.orEmpty()) ?: return true
        return mana + (stats.overflowMana ?: 0f).coerceAtLeast(0f) >= cost
    }

    private fun handBack() {
        if (!borrow.isActive || !swap.ready) return
        borrow.release()
    }
}

private val MANA_COST = Regex("""Mana Cost: ([\d,]+)""")

internal fun manaCost(lore: List<String>): Int? =
    lore.firstNotNullOfOrNull { MANA_COST.find(it) }?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
