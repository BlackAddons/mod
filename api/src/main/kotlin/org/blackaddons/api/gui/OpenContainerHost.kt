package org.blackaddons.api.gui

import org.blackaddons.api.world.ItemHost

/** Reads and drives the open container screen. [ItemHost] owns the raw item format, [ScreenDraw] draws. */
interface OpenContainerHost {
    /** Upper (non-player) slots of the open container, empty slots as null; null if nothing is open. */
    fun capture(): CapturedSlots?

    /** Unformatted. Headless menus have one too. */
    fun title(): String?

    fun hoveredItemRaw(): String?

    /** Includes the creative inventory. */
    fun isPlayerInventoryOpen(): Boolean

    /** The 36 player slots (hotbar first) of the open menu, headless ones included, with their menu indices for [clickSlot]. */
    fun capturePlayerInventory(): CapturedPlayerInventory?

    fun carriedItemRaw(): String?

    /** Identity of the menu receiving [clickSlot]. */
    fun interactionId(): Int?

    /** Clicks a slot like the player would ([button] 0=left/1=right; [quickMove] = shift). No-op if out of range. */
    fun clickSlot(slotIndex: Int, button: Int, quickMove: Boolean)

    /** Same set and order as [capture]; 0 if nothing is open. */
    fun chestSlotCount(): Int

    /** Puts a real chest slot at GUI coords, giving custom layouts native clicks. Call per frame: menus get rebuilt. */
    fun moveChestSlot(index: Int, screenX: Int, screenY: Int)

    /** Moves a chest slot off-screen. No-op if out of range or nothing is open. */
    fun parkChestSlot(index: Int)

    fun close()

    fun openPlayerInventory()
}

/** Row-major, [rows] x [cols]; null = empty slot. */
data class CapturedSlots(val rows: Int, val cols: Int, val raw: List<String?>)

data class CapturedPlayerInventory(
    val interactionId: Int,
    val raw: List<String?>,
    val menuSlots: List<Int>,
)
