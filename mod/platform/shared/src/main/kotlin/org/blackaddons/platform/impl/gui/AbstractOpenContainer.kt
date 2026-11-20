package org.blackaddons.platform.impl.gui
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.world.inventory.Slot
import org.blackaddons.api.gui.CapturedPlayerInventory
import org.blackaddons.api.gui.CapturedSlots
import org.blackaddons.api.gui.OpenContainerHost
import org.blackaddons.api.player.InventorySlots
import org.blackaddons.platform.compat.currentScreen
import org.blackaddons.platform.impl.world.MinecraftItems
import org.blackaddons.platform.mixin.gui.AbstractContainerScreenAccessor
import org.blackaddons.platform.mixin.gui.SlotAccessor
import org.blackaddons.platform.util.onClient

/**
 * [OpenContainerHost] over the open menu. Item serialization lives in [MinecraftItems], only [sendClick] is
 * version-specific (`ClickType` vs `ContainerInput`).
 */
abstract class AbstractOpenContainer : OpenContainerHost {

    protected val mc get() = Minecraft.getInstance()

    /** Chest slots (not player inventory), first and in order. */
    private fun chestSlots(): List<Slot> {
        val player = mc.player ?: return emptyList()
        val inv = player.inventory
        return player.containerMenu.slots.filter { it.container !== inv }
    }

    override fun capture(): CapturedSlots? {
        val chest = chestSlots()
        if (chest.isEmpty() || chest.size % COLS != 0) return null
        return CapturedSlots(chest.size / COLS, COLS, chest.map { MinecraftItems.serialize(it.item) })
    }

    override fun title(): String? =
        (mc.currentScreen as? AbstractContainerScreen<*>)?.title?.string ?: HeadlessMenus.title()

    override fun hoveredItemRaw(): String? {
        val screen = mc.currentScreen as? AbstractContainerScreen<*> ?: return null
        val slot = (screen as AbstractContainerScreenAccessor).`blackaddons$getHoveredSlot`() ?: return null
        return if (slot.hasItem()) MinecraftItems.serialize(slot.item) else null
    }

    override fun isPlayerInventoryOpen(): Boolean {
        val player = mc.player ?: return false
        val screen = mc.currentScreen
        return screen is CreativeModeInventoryScreen || screen is AbstractContainerScreen<*> && player.containerMenu === player.inventoryMenu
    }

    override fun capturePlayerInventory(): CapturedPlayerInventory? {
        val player = mc.player ?: return null
        val menu = player.containerMenu
        val slots = menu.slots.withIndex()
            .filter { (_, slot) -> slot.container === player.inventory && slot.containerSlot in 0 until InventorySlots.STORAGE }
            .sortedBy { (_, slot) -> slot.containerSlot }
        if (slots.size != InventorySlots.STORAGE) return null
        return CapturedPlayerInventory(
            interactionId = menu.containerId,
            raw = slots.map { (_, slot) -> MinecraftItems.serialize(slot.item) },
            menuSlots = slots.map { it.index },
        )
    }

    override fun carriedItemRaw(): String? =
        mc.player?.containerMenu?.carried?.let(MinecraftItems::serialize)

    override fun interactionId(): Int? = mc.player?.containerMenu?.containerId

    final override fun clickSlot(slotIndex: Int, button: Int, quickMove: Boolean) = onClient {
        val menu = mc.player?.containerMenu ?: return@onClient
        if (slotIndex < 0 || slotIndex >= menu.slots.size) return@onClient
        sendClick(menu.containerId, slotIndex, button, quickMove)
    }

    /** [quickMove] = shift-click. */
    protected abstract fun sendClick(containerId: Int, slotIndex: Int, button: Int, quickMove: Boolean)

    override fun chestSlotCount(): Int = chestSlots().size

    override fun moveChestSlot(index: Int, screenX: Int, screenY: Int) {
        val acc = mc.currentScreen as? AbstractContainerScreenAccessor ?: return
        val slot = chestSlots().getOrNull(index) ?: return
        // Relative to leftPos/topPos.
        val a = slot as SlotAccessor
        a.`blackaddons$setX`(screenX - acc.`blackaddons$getLeftPos`())
        a.`blackaddons$setY`(screenY - acc.`blackaddons$getTopPos`())
    }

    override fun parkChestSlot(index: Int) {
        val slot = chestSlots().getOrNull(index) ?: return
        val a = slot as SlotAccessor
        a.`blackaddons$setX`(OFF_SCREEN)
        a.`blackaddons$setY`(OFF_SCREEN)
    }

    // Hopped: ends in Gui.setScreen(null), like MinecraftScreen.hide.
    override fun close() = onClient {
        mc.player?.closeContainer()
    }

    override fun openPlayerInventory() = onClient {
        val player = mc.player ?: return@onClient
        mc.setScreenAndShow(InventoryScreen(player))
    }

    private companion object {
        const val COLS = 9
        const val OFF_SCREEN = -10000
    }
}
