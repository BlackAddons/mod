package org.blackaddons.api.ui.widget.dropdown
import org.blackaddons.api.ui.anim.Animation
import org.blackaddons.api.ui.layout.hit
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.InputTarget
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.MouseDragInput
import org.blackaddons.api.ui.widget.MouseScrollInput
import java.awt.Color

/**
 * Dropdown with optional Unifont glyph icons. Call [render] in place and [renderOverlay] last; forward clicks
 * to [mouseClicked] first so the open list gets priority.
 */
class Dropdown(maxVisibleItems: Int = 5) : PopupControl, InputTarget {

    data class Icon(val glyph: String, val color: Color)

    data class Item(val label: String, val icon: Icon? = null)

    private val popup = DropdownPopup(this).also { it.maxVisibleItems = maxVisibleItems }

    var items: List<Item> = emptyList()
    var selectedIndex: Int = 0
    override var open: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                if (value) {
                    DropdownManager.onOpen(this)
                    onOpen?.invoke()
                } else {
                    DropdownManager.onClose(this)
                }
            }
        }

    var maxVisibleItems: Int by popup::maxVisibleItems

    /** For dropdowns near the screen bottom. */
    var dropUp: Boolean by popup::dropUp

    var onSelect: ((Int) -> Unit)? = null

    var onOpen: (() -> Unit)? = null

    /** A field's bottom line, for a dropdown on a surface of its own colour. It folds into its middle on hover. */
    var underline: Boolean = false

    private val underlineFold = Animation(durationMs = Theme.ANIM_HOVER.toLong())

    override val isOverlaying: Boolean get() = open

    private val selected: Item? get() = items.getOrNull(selectedIndex)

    override fun closeImmediately() {
        open = false
        DropdownManager.onClose(this)
        popup.reset()
    }

    fun render(x: Float, y: Float, width: Float, height: Float, mouseX: Float, mouseY: Float) {
        popup.drawBox(x, y, width, height, mouseX, mouseY)
        selected?.let { drawItem(it, x, y, width - CHEVRON_SPACE, height, Theme.TEXT_PRIMARY) }
        if (underline) drawUnderline(x, y, width, height, mouseX, mouseY)
    }

    // Open or hovered, the box's own fill shows, so the line gives way to it.
    private fun drawUnderline(x: Float, y: Float, width: Float, height: Float, mouseX: Float, mouseY: Float) {
        underlineFold.animateTo(if (open || hit(mouseX, mouseY, x, y, width, height)) 1f else 0f)
        val lineW = width * (1f - underlineFold.get())
        if (lineW > 0f) Render.rect(x + (width - lineW) / 2f, y + height - 1f, lineW, 1f, Theme.BORDER)
    }

    /** Fits every item, padding and chevron included. */
    fun preferredWidth(): Float = items.maxOfOrNull { item ->
        val iconWidth = item.icon?.let { Render.textWidth(it.glyph, Theme.FONT_BODY, Fonts.unifont) + Theme.PADDING_SMALL } ?: 0f
        iconWidth + Render.textWidth(item.label, Theme.FONT_SMALL, Fonts.medium)
    }?.plus(Theme.PADDING * 2f + CHEVRON_SPACE) ?: 0f

    /** Call last. */
    fun renderOverlay(mouseX: Float, mouseY: Float) {
        popup.drawList(items.size, mouseX, mouseY) { i, rowX, rowY, rowW, _ ->
            val color = if (i == selectedIndex) Theme.ACCENT else Theme.TEXT_PRIMARY
            drawItem(items[i], rowX, rowY, rowW, popup.box.h, color)
        }
    }

    override fun mouseClicked(mouse: MouseButtonInput): Boolean {
        // Not drawn this frame -> closed, not clickable.
        if (!popup.box.isDrawn) {
            if (open) open = false
            return false
        }
        if (open) {
            if (popup.scrollClicked(mouse)) return true
            val clicked = popup.rowAt(mouse.x, mouse.y, items.size)
            if (clicked != null) {
                open = false
                if (clicked != selectedIndex) {
                    selectedIndex = clicked
                    onSelect?.invoke(clicked)
                }
                return true
            }
            // Consume only if it was the box itself.
            open = false
            return popup.box.has(mouse.x, mouse.y)
        }
        if (mouse.isLeft && popup.box.has(mouse.x, mouse.y)) {
            open = true
            return true
        }
        return false
    }

    override fun mouseDragged(drag: MouseDragInput): Boolean =
        popup.mouseDragged(drag)

    override fun mouseReleased(mouse: MouseButtonInput): Boolean =
        popup.mouseReleased(mouse)

    override fun mouseScrolled(wheel: MouseScrollInput): Boolean =
        popup.mouseScrolled(wheel)

    private fun drawItem(item: Item, x: Float, y: Float, width: Float, height: Float, textColor: Color) {
        var tx = x + Theme.PADDING
        item.icon?.let {
            Render.text(it.glyph, tx, Render.centerY(y, height, Theme.FONT_BODY, Fonts.unifont), Theme.FONT_BODY, it.color, Fonts.unifont)
            tx += Render.textWidth(it.glyph, Theme.FONT_BODY, Fonts.unifont) + Theme.PADDING_SMALL
        }
        val availW = (x + width - Theme.PADDING - tx).coerceAtLeast(10f)
        Render.text(item.label, tx, Render.centerY(y, height, Theme.FONT_SMALL, Fonts.medium), Theme.FONT_SMALL, textColor, Fonts.medium, maxWidth = availW)
    }

    private companion object {
        const val CHEVRON_SPACE = 16f
    }
}
