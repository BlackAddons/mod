package org.blackaddons.api.player

import org.blackaddons.api.Platform
import org.blackaddons.api.world.ItemInfo

object Hotbar {

    const val SIZE = 9

    val slots: IntRange = 0 until SIZE

    /**
     * First slot whose item [match] accepts. [preferred] is checked first - reading a slot builds a full
     * [ItemInfo], so sweeping every tick isn't free.
     */
    fun find(preferred: Int? = null, match: (ItemInfo) -> Boolean): Int? {
        if (preferred != null && preferred in slots && holds(preferred, match)) return preferred
        return slots.firstOrNull { holds(it, match) }
    }

    private fun holds(slot: Int, match: (ItemInfo) -> Boolean): Boolean =
        Platform.player.inventoryItem(slot)?.let(match) == true
}

/**
 * A hotbar slot borrowed for a feature. [take] remembers the player's slot only on the first call; [release]
 * returns there, [forget] ends the loan without moving (use it when the player switched themselves).
 */
class HotbarBorrow {

    var slot: Int? = null
        private set

    private var restoreTo: Int? = null

    val isActive: Boolean get() = slot != null

    fun take(slot: Int, restore: Boolean = true) {
        if (slot !in Hotbar.slots) return
        if (restore && restoreTo == null) restoreTo = Platform.interaction.selectedSlot
        this.slot = slot
        if (Platform.interaction.selectedSlot != slot) Platform.interaction.selectHotbarSlot(slot)
    }

    fun release() {
        restoreTo?.let(Platform.interaction::selectHotbarSlot)
        forget()
    }

    fun forget() {
        slot = null
        restoreTo = null
    }
}
