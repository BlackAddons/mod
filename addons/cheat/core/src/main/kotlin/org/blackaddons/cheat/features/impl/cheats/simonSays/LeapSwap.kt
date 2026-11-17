package org.blackaddons.cheat.features.impl.cheats.simonSays

import org.blackaddons.api.Platform
import org.blackaddons.api.player.ClientAction
import org.blackaddons.api.player.Hotbar
import org.blackaddons.api.skyblock.SkyblockItem
import org.blackaddons.api.ui.toast.ToastKind
import org.blackaddons.api.ui.toast.Toasts
import org.blackaddons.api.util.I18n
import kotlin.random.Random

/*
 * When a device AutoSS pressed buttons on is done, swaps to the leap and optionally opens it, 1-2 ticks apart. Reads
 * the hologram since mods hide the chat line.
 */
internal class LeapSwap(private val owner: Any) {

    /* We pressed a button this round, so the finish is ours. */
    private var pressed = false
    private var swapAtTick = NOT_SCHEDULED
    private var openAtTick = NOT_SCHEDULED

    fun pressed() {
        pressed = true
    }

    /* [aimBusy]: with No Rotate the server aim can lag behind the cursor. */
    fun tick(tick: Int, open: Boolean, aimBusy: Boolean) {
        if (pressed && tick % DeviceHologram.POLL_TICKS == 0 && DeviceHologram.read() == DeviceHologram.ACTIVE) {
            pressed = false
            swapAtTick = tick + reactionTicks()
        }
        if (aimBusy) return
        if (swapAtTick != NOT_SCHEDULED && tick >= swapAtTick) {
            swapAtTick = NOT_SCHEDULED
            val slot = Hotbar.find(match = SkyblockItem.SPIRIT_LEAP)
            if (slot == null) {
                Toasts.show(I18n.trOr("blackaddons.simon_says_cheats.no_leap", "Auto Simon Says: no Spirit Leap or Infinileap in your hotbar."), kind = ToastKind.ERROR)
                return
            }
            Platform.interaction.selectHotbarSlot(slot)
            if (open) openAtTick = tick + reactionTicks()
        }
        if (openAtTick != NOT_SCHEDULED && tick >= openAtTick) {
            openAtTick = NOT_SCHEDULED
            if (Platform.screenHost.anyScreenOpen || !holdsLeap()) return
            Platform.interaction.submit(ClientAction.UseItem, owner = owner)
        }
    }

    fun reset() {
        pressed = false
        swapAtTick = NOT_SCHEDULED
        openAtTick = NOT_SCHEDULED
    }

    private fun holdsLeap(): Boolean =
        Platform.player.inventoryItem(Platform.interaction.selectedSlot)?.let(SkyblockItem.SPIRIT_LEAP) == true

    private fun reactionTicks(): Int = Random.nextInt(1, 3)

    private companion object {
        const val NOT_SCHEDULED = -1
    }
}
