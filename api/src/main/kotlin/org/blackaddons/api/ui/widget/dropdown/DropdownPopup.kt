package org.blackaddons.api.ui.widget.dropdown
import org.blackaddons.api.ui.anim.Animation
import org.blackaddons.api.ui.anim.isVisible
import org.blackaddons.api.ui.layout.Rect
import org.blackaddons.api.ui.layout.hit
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.Chevron
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.MouseDragInput
import org.blackaddons.api.ui.widget.MouseScrollInput
import org.blackaddons.api.ui.widget.Scroll
import java.awt.Color

// Shared box and list for Dropdown and MultiSelectDropdown; the owner draws rows and handles clicks.
internal class DropdownPopup(private val control: PopupControl) {

    // Clickable box, and whether it was drawn this frame.
    val box = Rect()

    var dropUp = false
    var maxVisibleItems = 5

    // Keeps drawing after `open` clears so it collapses smoothly.
    private val reveal = Animation()
    private val scroll = Scroll()

    fun drawBox(x: Float, y: Float, width: Float, height: Float, mouseX: Float, mouseY: Float) {
        box.set(x, y, width, height)
        reveal.animateTo(if (control.open) 1f else 0f)
        val bg = when {
            control.open -> Theme.SURFACE_ACTIVE
            hit(mouseX, mouseY, x, y, width, height) -> Theme.SURFACE_HOVER
            else -> Theme.SURFACE
        }
        Render.rect(x, y, width, height, bg, Theme.RADIUS_SMALL)
        Chevron.draw(x + width - 9f, y + height / 2f, 5f, 1.6f, 1f - 2f * reveal.get())
    }

    // drawRow gets (index, y, width, hovered).
    fun drawList(rowCount: Int, mouseX: Float, mouseY: Float, drawRow: (Int, Float, Float, Boolean) -> Unit) {
        if (rowCount == 0) return
        val shown = reveal.get()
        if (!shown.isVisible) return

        val rowH = box.h
        val listH = listHeight(rowCount)
        val top = listTop(rowCount)
        val offset = scroll.update(box.x, top, box.w, listH, rowCount * rowH)

        val visH = listH * shown
        Render.pushScissor(box.x, if (dropUp) box.y - visH else top, box.w, visH)
        Render.rect(box.x, top, box.w, listH, POPUP_BG, Theme.RADIUS_SMALL)
        Render.hollowRect(box.x, top, box.w, listH, 1.5f, Theme.BORDER.withAlpha(220), Theme.RADIUS_SMALL)

        val rowW = if (rowCount > maxVisibleItems) box.w - SCROLL_GUTTER else box.w
        for (i in 0 until rowCount) {
            val rowY = top + i * rowH - offset
            if (rowY + rowH < top || rowY > top + listH) continue
            val hovered = control.open && hit(mouseX, mouseY, box.x, rowY, rowW, rowH)
            if (hovered) Render.rect(box.x, rowY, rowW, rowH, Theme.SURFACE_HOVER, Theme.RADIUS_SMALL)
            drawRow(i, rowY, rowW, hovered)
        }
        scroll.draw()
        Render.popScissor()
    }

    fun onList(mouseX: Float, mouseY: Float, rowCount: Int): Boolean =
        hit(mouseX, mouseY, box.x, listTop(rowCount), box.w, listHeight(rowCount))

    fun rowAt(mouseX: Float, mouseY: Float, rowCount: Int): Int? {
        if (!onList(mouseX, mouseY, rowCount)) return null
        return ((mouseY - listTop(rowCount) + scroll.offset) / box.h).toInt().takeIf { it in 0 until rowCount }
    }

    // The bar takes a press before the rows.
    fun scrollClicked(mouse: MouseButtonInput): Boolean = control.open && scroll.mouseClicked(mouse)

    fun mouseDragged(drag: MouseDragInput): Boolean =
        scroll.mouseDragged(drag)

    fun mouseReleased(mouse: MouseButtonInput): Boolean = scroll.mouseReleased(mouse)

    // Closed, it must not swallow the wheel where it last hung.
    fun mouseScrolled(wheel: MouseScrollInput): Boolean =
        control.open && scroll.mouseScrolled(wheel)

    fun reset() {
        reveal.set(0f)
        scroll.reset()
    }

    private fun listHeight(rowCount: Int) = minOf(rowCount, maxVisibleItems) * box.h

    private fun listTop(rowCount: Int) = if (dropUp) box.y - listHeight(rowCount) else box.y + box.h

    private companion object {
        val POPUP_BG = Color(16, 18, 24, 255)
        const val SCROLL_GUTTER = Scroll.LANE_WIDTH + 2f
    }
}
