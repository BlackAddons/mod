package org.blackaddons.api.ui.screen

import org.blackaddons.api.ui.icon.LucideIcons
import org.blackaddons.api.ui.icon.VectorIcon
import org.blackaddons.api.ui.layout.Rect
import org.blackaddons.api.ui.layout.centered
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.Scroll
import org.blackaddons.api.ui.widget.UiMouse

/**
 * Right-click menu at the cursor (`UiManager.contextMenu(...)`); scrolls if too tall. An entry with
 * [Item.confirm] closes the menu and asks via [ConfirmPopover].
 */
class ContextMenu(
    private val entries: List<Entry>,
    private val anchorX: Float = UiMouse.x,
    private val anchorY: Float = UiMouse.y,
) : Popover() {

    sealed interface Entry

    class Item(
        val label: String,
        val icon: VectorIcon? = null,
        val danger: Boolean = false,
        val enabled: Boolean = true,
        val confirm: String? = null,
        /** Checked state, read every frame; [icon] wins when set. */
        val checked: (() -> Boolean)? = null,
        val onClick: () -> Unit,
    ) : Entry

    data object Separator : Entry

    private val scroll = widgets.add(Scroll())
    private val boxes = entries.associateWith { Rect() }

    override fun render(mouseX: Float, mouseY: Float, deltaTicks: Float) {
        val items = entries.filterIsInstance<Item>()
        val iconRoom = if (items.any { it.icon != null || it.checked != null }) ICON + ICON_GAP else 0f
        val labelW = items.maxOfOrNull { Render.textWidth(it.label, Theme.FONT_SMALL, Fonts.regular) } ?: 0f
        val w = maxOf(MIN_W, labelW + iconRoom + Theme.PADDING * 2f + PAD * 2f)
        val full = entries.sumOf { heightOf(it).toDouble() }.toFloat() + PAD * 2f

        // Opens down-right, flipping at edges.
        val screenW = UiManager.screenWidth
        val screenH = UiManager.screenHeight
        val h = minOf(full, maxOf(ITEM_H, screenH - MARGIN * 2f))
        val x = (if (anchorX + w + MARGIN <= screenW) anchorX else anchorX - w).coerceIn(MARGIN, maxOf(MARGIN, screenW - w - MARGIN))
        val y = (if (anchorY + h + MARGIN <= screenH) anchorY else anchorY - h).coerceIn(MARGIN, maxOf(MARGIN, screenH - h - MARGIN))
        card.set(x, y, w, h)

        drawCard {
            scroll.render(x, y, w, h, full) { offset ->
                var ey = y + PAD - offset
                for (entry in entries) {
                    val eh = heightOf(entry)
                    val box = boxes.getValue(entry).apply { set(x + PAD, ey, w - PAD * 2f, eh) }
                    ey += eh
                    /* Scrolled out of view, so it takes no clicks either. */
                    if (box.y + eh <= y || box.y >= y + h) continue
                    when (entry) {
                        Separator -> Render.rect(box.x + Theme.PADDING_SMALL, box.y + eh / 2f, box.w - Theme.PADDING_SMALL * 2f, 1f, Theme.BORDER)
                        is Item -> drawItem(entry, box, iconRoom, mouseX, mouseY)
                    }
                }
            }
        }
    }

    private fun drawItem(item: Item, box: Rect, iconRoom: Float, mouseX: Float, mouseY: Float) {
        val hovered = widgets.clickable(box.x, box.y, box.w, box.h) { if (item.enabled) choose(item) } && item.enabled
        if (hovered) {
            Render.rect(box, if (item.danger) Theme.DANGER.withAlpha(50) else Theme.SURFACE_HOVER, Theme.RADIUS_SMALL)
        }
        val color = when {
            !item.enabled -> Theme.TEXT_DISABLED
            item.danger -> Theme.DANGER
            hovered -> Theme.TEXT_PRIMARY
            else -> Theme.TEXT_SECONDARY
        }
        val left = box.x + Theme.PADDING
        val icon = item.icon ?: LucideIcons.circleCheck.takeIf { item.checked?.invoke() == true }
        icon?.let { Render.icon(it, left, centered(box.y, box.h, ICON), ICON, color) }
        Render.text(
            item.label, left + iconRoom,
            Render.centerY(box.y, box.h, Theme.FONT_SMALL, Fonts.regular),
            Theme.FONT_SMALL, color, Fonts.regular,
        )
    }

    private fun choose(item: Item) {
        UiManager.pop()
        val prompt = item.confirm ?: return item.onClick()
        val box = boxes.getValue(item)
        UiManager.push(ConfirmPopover(box.x, box.y, box.w, box.h, message = prompt, danger = item.danger, onConfirm = item.onClick))
    }

    private fun heightOf(entry: Entry): Float = if (entry is Item) ITEM_H else SEPARATOR_H

    private companion object {
        const val ITEM_H = 20f
        const val SEPARATOR_H = 7f
        const val MIN_W = 120f
        const val PAD = 3f
        const val ICON = 11f
        const val ICON_GAP = 6f
    }
}
