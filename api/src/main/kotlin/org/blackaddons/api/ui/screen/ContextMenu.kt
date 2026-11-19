package org.blackaddons.api.ui.screen

import org.blackaddons.api.feature.settings.impl.KeybindSetting
import org.blackaddons.api.input.Keys
import org.blackaddons.api.ui.icon.LucideIcons
import org.blackaddons.api.ui.icon.VectorIcon
import org.blackaddons.api.ui.layout.Rect
import org.blackaddons.api.ui.layout.centered
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.KeyInput
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.Scroll
import org.blackaddons.api.ui.widget.UiMouse
import java.awt.Color

/**
 * Right-click menu at the cursor (`UiManager.contextMenu(...)`); scrolls if too tall. An entry with
 * [Item.confirm] closes the menu and asks via [ConfirmPopover]. A [KeybindItem] binds in place and keeps it open.
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

    /** Click to capture [keybind]: any key or non-left mouse button binds it, `Esc` clears, and left-click cancels. */
    class KeybindItem(
        val label: String,
        val keybind: KeybindSetting,
        val icon: VectorIcon? = null,
    ) : Entry

    data object Separator : Entry

    private val scroll = widgets.add(Scroll())
    private val boxes = entries.associateWith { Rect() }
    private var listening: KeybindItem? = null

    override fun render(mouseX: Float, mouseY: Float, deltaTicks: Float) {
        val hasIcon = entries.any { it is Item && (it.icon != null || it.checked != null) || it is KeybindItem && it.icon != null }
        val iconRoom = if (hasIcon) ICON + ICON_GAP else 0f
        val labelW = entries.maxOfOrNull(::labelWidth) ?: 0f
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
                        is KeybindItem -> drawKeybind(entry, box, iconRoom)
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
        drawLabel(item.label, item.icon ?: LucideIcons.circleCheck.takeIf { item.checked?.invoke() == true }, color, box, iconRoom)
    }

    private fun drawKeybind(item: KeybindItem, box: Rect, iconRoom: Float) {
        val active = listening === item
        val hovered = widgets.clickable(box.x, box.y, box.w, box.h) { listening = item } || active
        if (hovered) Render.rect(box, Theme.SURFACE_HOVER, Theme.RADIUS_SMALL)
        drawLabel(item.label, item.icon, if (hovered) Theme.TEXT_PRIMARY else Theme.TEXT_SECONDARY, box, iconRoom)
        val key = keyLabel(item)
        Render.text(
            key, box.x + box.w - Theme.PADDING - Render.textWidth(key, Theme.FONT_SMALL, Fonts.regular),
            Render.centerY(box.y, box.h, Theme.FONT_SMALL, Fonts.regular),
            Theme.FONT_SMALL, if (active) Theme.ACCENT else Theme.TEXT_DISABLED, Fonts.regular,
        )
    }

    private fun drawLabel(label: String, icon: VectorIcon?, color: Color, box: Rect, iconRoom: Float) {
        val left = box.x + Theme.PADDING
        icon?.let { Render.icon(it, left, centered(box.y, box.h, ICON), ICON, color) }
        Render.text(
            label, left + iconRoom,
            Render.centerY(box.y, box.h, Theme.FONT_SMALL, Fonts.regular),
            Theme.FONT_SMALL, color, Fonts.regular,
        )
    }

    private fun keyLabel(item: KeybindItem): String = if (listening === item) "..." else item.keybind.displayName

    private fun labelWidth(entry: Entry): Float = when (entry) {
        is Item -> Render.textWidth(entry.label, Theme.FONT_SMALL, Fonts.regular)
        is KeybindItem -> Render.textWidth(entry.label + keyLabel(entry), Theme.FONT_SMALL, Fonts.regular) + KEY_GAP
        Separator -> 0f
    }

    override fun keyPressed(key: KeyInput): Boolean {
        val item = listening ?: return super.keyPressed(key)
        if (key.code == Keys.ESCAPE) item.keybind.unbind() else item.keybind.bindKeyboard(key.code)
        listening = null
        return true
    }

    override fun mouseClicked(mouse: MouseButtonInput): Boolean {
        val item = listening ?: return super.mouseClicked(mouse)
        if (!mouse.isLeft) item.keybind.bindMouse(mouse.button)
        listening = null
        return true
    }

    private fun choose(item: Item) {
        UiManager.pop()
        val prompt = item.confirm ?: return item.onClick()
        val box = boxes.getValue(item)
        UiManager.push(ConfirmPopover(box.x, box.y, box.w, box.h, message = prompt, danger = item.danger, onConfirm = item.onClick))
    }

    private fun heightOf(entry: Entry): Float = if (entry is Separator) SEPARATOR_H else ITEM_H

    private companion object {
        const val ITEM_H = 20f
        const val SEPARATOR_H = 7f
        const val MIN_W = 120f
        const val PAD = 3f
        const val ICON = 11f
        const val ICON_GAP = 6f
        const val KEY_GAP = 16f
    }
}
