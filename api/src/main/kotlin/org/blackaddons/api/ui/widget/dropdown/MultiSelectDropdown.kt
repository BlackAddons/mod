package org.blackaddons.api.ui.widget.dropdown
import org.blackaddons.api.ui.layout.centered
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.Tooltip
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.InputTarget
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.MouseDragInput
import org.blackaddons.api.ui.widget.MouseScrollInput

class MultiSelectDropdown(
    var placeholder: String = "Select...",
    maxVisibleItems: Int = 5,
) : PopupControl, InputTarget {

    /** [tooltip] shows while hovered in the open list. */
    data class Item(val id: String, val label: String, val tooltip: String? = null)

    private val popup = DropdownPopup(this).also { it.maxVisibleItems = maxVisibleItems }

    var items: List<Item> = emptyList()
    val selectedIds: MutableSet<String> = mutableSetOf()
    override var open: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                if (value) DropdownManager.onOpen(this) else DropdownManager.onClose(this)
            }
        }
    var maxVisibleItems: Int by popup::maxVisibleItems
    var dropUp: Boolean by popup::dropUp
    var selectedFormatter: ((Set<String>, List<Item>) -> String)? = null
    var onToggle: ((String, Boolean) -> Unit)? = null

    override val isOverlaying: Boolean get() = open

    override fun closeImmediately() {
        open = false
        DropdownManager.onClose(this)
        popup.reset()
    }

    fun render(x: Float, y: Float, width: Float, height: Float, mouseX: Float, mouseY: Float) {
        popup.drawBox(x, y, width, height, mouseX, mouseY)
        val label = selectedFormatter?.invoke(selectedIds, items) ?: when {
            selectedIds.isEmpty() -> placeholder
            selectedIds.size == 1 -> items.firstOrNull { it.id in selectedIds }?.label ?: placeholder
            selectedIds.size <= 2 -> items.filter { it.id in selectedIds }.joinToString(", ") { it.label }
            else -> "${selectedIds.size} Selected"
        }
        val availW = (width - 24f).coerceAtLeast(10f)
        Render.text(label, x + Theme.PADDING, Render.centerY(y, height, TextStyle.SMALL_PRIMARY_MEDIUM), TextStyle.SMALL_PRIMARY_MEDIUM, maxWidth = availW)
    }

    fun renderOverlay(mouseX: Float, mouseY: Float) {
        popup.drawList(items.size, mouseX, mouseY) { i, rowY, rowW, hovered ->
            drawRow(items[i], rowY, rowW)
            if (hovered) items[i].tooltip?.let(Tooltip::show)
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
                val item = items[clicked]
                val checked = item.id !in selectedIds
                if (checked) selectedIds.add(item.id) else selectedIds.remove(item.id)
                onToggle?.invoke(item.id, checked)
                return true
            }
            // Consume only if it was the box itself.
            open = false
            return popup.box.has(mouse.x, mouse.y)
        }
        if (popup.box.has(mouse.x, mouse.y)) {
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

    private fun drawRow(item: Item, rowY: Float, rowW: Float) =
        drawCheckRow(item.label, item.id in selectedIds, popup.box.x, rowY, rowW, popup.box.h)
}

private const val CHECK_SIZE = 13f

internal fun drawCheckRow(label: String, checked: Boolean, x: Float, rowY: Float, rowW: Float, rowH: Float) {
    val boxX = x + Theme.PADDING
    val boxY = centered(rowY, rowH, CHECK_SIZE)
    if (checked) {
        Render.rect(boxX, boxY, CHECK_SIZE, CHECK_SIZE, Theme.ACCENT, 3f)
        Render.hollowRect(boxX, boxY, CHECK_SIZE, CHECK_SIZE, 1.2f, Theme.ACCENT.withAlpha(220), 3f)
        Render.line(boxX + 2.8f, boxY + CHECK_SIZE / 2f + 0.5f, boxX + CHECK_SIZE / 2.3f, boxY + CHECK_SIZE - 3.2f, 1.5f, Theme.TEXT_PRIMARY)
        Render.line(boxX + CHECK_SIZE / 2.3f, boxY + CHECK_SIZE - 3.2f, boxX + CHECK_SIZE - 2.8f, boxY + 3.2f, 1.5f, Theme.TEXT_PRIMARY)
    } else {
        Render.rect(boxX, boxY, CHECK_SIZE, CHECK_SIZE, Theme.SURFACE_ACTIVE, 3f)
        Render.hollowRect(boxX, boxY, CHECK_SIZE, CHECK_SIZE, 1.2f, Theme.BORDER.withAlpha(200), 3f)
    }
    val textX = boxX + CHECK_SIZE + 8f
    val textAvail = (x + rowW - Theme.PADDING - textX).coerceAtLeast(10f)
    val color = if (checked) Theme.TEXT_PRIMARY else Theme.TEXT_SECONDARY
    Render.text(label, textX, Render.centerY(rowY, rowH, Theme.FONT_SMALL, Fonts.medium), Theme.FONT_SMALL, color, Fonts.medium, maxWidth = textAvail)
}

internal fun checkRowWidth(label: String): Float =
    Theme.PADDING * 2f + CHECK_SIZE + 8f + Render.textWidth(label, Theme.FONT_SMALL, Fonts.medium)
