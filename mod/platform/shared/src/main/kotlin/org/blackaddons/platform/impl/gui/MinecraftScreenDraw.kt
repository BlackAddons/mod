package org.blackaddons.platform.impl.gui
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.world.item.ItemStack
import org.blackaddons.api.gui.ScreenDraw
import org.blackaddons.api.ui.anim.AnimationMap
import org.blackaddons.api.ui.anim.isVisible
import org.blackaddons.platform.compat.currentScreen
import org.blackaddons.platform.compat.drawTooltip
import org.blackaddons.platform.impl.world.MinecraftItems
import org.blackaddons.platform.mixin.gui.AbstractContainerScreenAccessor

/** [ScreenDraw] via the caller's extract-phase [GuiGraphicsExtractor] `ctx`. Items use [MinecraftItems.deserialize]. */
object MinecraftScreenDraw : ScreenDraw {

    private val mc get() = Minecraft.getInstance()

    // Dark inventory: near-black backing, slightly lighter cells.
    private const val INV_BACKING = 0xFF101013.toInt()
    private const val INV_CELL = 0xFF2E2E33.toInt()
    private const val INV_HOVER_RGB = 0xFFFFFF
    private const val INV_HOVER_ALPHA = 128

    private val invHover = AnimationMap<Int>()

    override fun fill(ctx: Any, x: Int, y: Int, w: Int, h: Int, argb: Int) {
        (ctx as? GuiGraphicsExtractor)?.fill(x, y, x + w, y + h, argb)
    }

    override fun text(ctx: Any, text: String, x: Int, y: Int, argb: Int) {
        (ctx as? GuiGraphicsExtractor)?.text(mc.font, text, x, y, argb)
    }

    override fun textCentered(ctx: Any, text: String, centerX: Int, y: Int, argb: Int) {
        (ctx as? GuiGraphicsExtractor)?.text(mc.font, text, centerX - mc.font.width(text) / 2, y, argb)
    }

    override fun textWidth(text: String): Int = mc.font.width(text)

    override fun item(ctx: Any, raw: String, x: Int, y: Int) {
        val g = ctx as? GuiGraphicsExtractor ?: return
        val stack = MinecraftItems.deserialize(raw) ?: return
        g.item(stack, x, y)
        g.itemDecorations(mc.font, stack, x, y)
    }

    override fun itemDecorations(ctx: Any, raw: String, x: Int, y: Int) {
        val g = ctx as? GuiGraphicsExtractor ?: return
        val stack = MinecraftItems.deserialize(raw) ?: return
        g.itemDecorations(mc.font, stack, x, y)
    }

    override fun tooltip(ctx: Any, raw: String, x: Int, y: Int) {
        val g = ctx as? GuiGraphicsExtractor ?: return
        drawTooltip(g, MinecraftItems.deserialize(raw) ?: return, x, y)
    }

    /** Draws now: `setTooltipForNextFrame` is first-wins, so ours got dropped after vanilla's. */
    private fun drawTooltip(g: GuiGraphicsExtractor, stack: ItemStack, x: Int, y: Int) {
        val lines = Screen.getTooltipFromItem(mc, stack).map { ClientTooltipComponent.create(it.visualOrderText) }
        g.drawTooltip(mc.font, lines, x, y)
    }

    override fun renderInventoryDark(ctx: Any, mouseX: Int, mouseY: Int) {
        val g = ctx as? GuiGraphicsExtractor ?: return
        val screen = mc.currentScreen as? AbstractContainerScreen<*> ?: return
        val acc = screen as AbstractContainerScreenAccessor
        val left = acc.`blackaddons$getLeftPos`()
        val top = acc.`blackaddons$getTopPos`()
        val inv = screen.menu.slots.takeLast(36) // player inventory

        val minX = left + inv.minOf { it.x }
        val maxX = left + inv.maxOf { it.x } + 16
        val minY = top + inv.minOf { it.y }
        val maxY = top + inv.maxOf { it.y } + 16
        g.fill(minX - 3, minY - 3, maxX + 3, maxY + 3, INV_BACKING)

        var hovered: ItemStack? = null
        for (slot in inv) {
            val x = left + slot.x
            val y = top + slot.y
            g.fill(x, y, x + 16, y + 16, INV_CELL) // 2px gap between cells
            val over = mouseX in x until x + 16 && mouseY in y until y + 16
            val hover = invHover[slot.containerSlot, over]
            if (hover.isVisible) g.fill(x, y, x + 16, y + 16, ((INV_HOVER_ALPHA * hover).toInt() shl 24) or INV_HOVER_RGB)
            val stack = slot.item
            if (!stack.isEmpty) {
                g.item(stack, x, y)
                g.itemDecorations(mc.font, stack, x, y)
                if (over) hovered = stack
            }
        }

        hovered?.let { drawTooltip(g, it, mouseX, mouseY) }
        // The render mixin suppresses vanilla's carried item, so draw it last.
        val carried = screen.menu.carried
        if (!carried.isEmpty) {
            g.item(carried, mouseX - 8, mouseY - 8)
            g.itemDecorations(mc.font, carried, mouseX - 8, mouseY - 8)
        }
    }

    override fun pushScissor(ctx: Any, x: Int, y: Int, w: Int, h: Int) {
        (ctx as? GuiGraphicsExtractor)?.enableScissor(x, y, x + w, y + h)
    }

    override fun popScissor(ctx: Any) {
        (ctx as? GuiGraphicsExtractor)?.disableScissor()
    }
}
