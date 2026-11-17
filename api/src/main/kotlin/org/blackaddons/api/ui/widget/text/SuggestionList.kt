package org.blackaddons.api.ui.widget.text

import org.blackaddons.api.ui.anim.Animation
import org.blackaddons.api.ui.anim.DecayAnimation
import org.blackaddons.api.ui.anim.Easing
import org.blackaddons.api.ui.layout.Rect
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.MouseScrollInput
import org.blackaddons.api.util.color.lerpColor
import java.awt.Color

/**
 * Autocomplete popup under a [TextInput]. Only reports the chosen string; the field writes it. Drawn in a
 * second pass ([draw]) to paint over what's below.
 */
internal class SuggestionList {

    /** Null disables autocomplete. */
    var supplier: ((String) -> List<String>)? = null

    /** Display only; the full value is inserted. */
    var display: ((String) -> String)? = null

    private var items: List<String> = emptyList()
    private var selected: Int = -1

    /** So it doesn't reopen on the text it just wrote. */
    private var dismissed: Boolean = false

    private val openAnim = Animation(durationMs = Theme.ANIM_FAST.toLong(), easing = Easing.easeOutCubic)
    private val bounds = Rect()

    private var scrollTarget: Float = 0f
    private val scrollGlide = DecayAnimation()
    private var draggingThumb = false
    private var dragStartY = 0f
    private var dragStartScroll = 0f
    private val scrollbarHover = Animation(durationMs = Theme.ANIM_HOVER.toLong())

    val isOpen: Boolean get() = items.isNotEmpty() && !dismissed && openAnim.target > 0f
    val hasSelection: Boolean get() = selected >= 0

    fun undismiss() {
        dismissed = false
    }

    fun close() {
        dismissed = false
        selected = -1
        openAnim.animateTo(0f)
    }

    fun clearBounds() = bounds.clear()

    /** Called every frame from the field's render. */
    fun refresh(text: String, fieldFocused: Boolean, x: Float, y: Float, w: Float, h: Float) {
        val supply = supplier
        if (!fieldFocused || supply == null || dismissed) {
            items = emptyList()
            selected = -1
            openAnim.animateTo(0f)
            return
        }

        val candidates = supply(text)
        items = if (text.isNotEmpty() && candidates.any { it.equals(text, ignoreCase = true) }) emptyList() else candidates
        if (items.isEmpty()) {
            selected = -1
            openAnim.animateTo(0f)
        } else {
            if (selected >= items.size) selected = items.size - 1
            openAnim.animateTo(1f)
        }
        bounds.set(x, y + h, w, 0f)
    }

    /** Call last. */
    fun draw(mouseX: Float, mouseY: Float) {
        val progress = openAnim.get()
        if (progress <= 0f || items.isEmpty()) return

        val listH = listHeight()
        val lx = bounds.x
        val ly = bounds.y
        val lw = bounds.w
        bounds.h = listH

        val maxScroll = maxScroll()
        scrollTarget = scrollTarget.coerceIn(0f, maxScroll)
        val scrollOffset = scrollGlide.get(scrollTarget)

        Render.globalAlpha(progress)
        Render.push()
        Render.translate(0f, (1f - progress) * -4f)

        Render.rect(lx, ly, lw, listH, POPUP_BG, Theme.RADIUS_SMALL)
        Render.hollowRect(lx, ly, lw, listH, 1.5f, Theme.BORDER.withAlpha(220), Theme.RADIUS_SMALL)

        Render.pushScissor(lx, ly, lw, listH)
        for (i in items.indices) {
            val iy = ly + i * ITEM_H - scrollOffset
            if (iy + ITEM_H < ly || iy > ly + listH) continue

            val hovered = mouseX in lx..(lx + lw) && mouseY in maxOf(ly, iy)..minOf(ly + listH, iy + ITEM_H)
            if (i == selected || hovered) {
                Render.rect(lx, iy, lw, ITEM_H, Theme.SURFACE_HOVER, Theme.RADIUS_SMALL)
            }
            Render.text(
                display?.invoke(items[i]) ?: items[i],
                lx + Theme.PADDING,
                Render.centerY(iy, ITEM_H, Theme.FONT_SMALL, Fonts.medium),
                Theme.FONT_SMALL,
                if (i == selected) Theme.ACCENT else Theme.TEXT_PRIMARY,
                Fonts.medium,
            )
        }
        Render.popScissor()

        if (maxScroll > 0f) drawScrollbar(lx, ly, lw, listH, scrollOffset, maxScroll, mouseX, mouseY)

        Render.pop()
        Render.globalAlpha(1f)
    }

    private fun drawScrollbar(lx: Float, ly: Float, lw: Float, listH: Float, scrollOffset: Float, maxScroll: Float, mouseX: Float, mouseY: Float) {
        val trackX = lx + lw - 4f
        val trackY = ly + 2f
        val trackH = listH - 4f
        val thumbH = thumbHeight(listH)
        val thumbY = trackY + (scrollOffset / maxScroll) * (trackH - thumbH)

        val hovered = mouseX in (trackX - 4f)..(lx + lw) && mouseY in trackY..(trackY + trackH)
        scrollbarHover.animateTo(if (draggingThumb || hovered) 1f else 0f)
        val thumbColor = lerpColor(Theme.TEXT_MUTED, Theme.TEXT_SECONDARY, scrollbarHover.get())

        Render.rect(trackX, trackY, 3f, trackH, Theme.BORDER.withAlpha(60), 1.5f)
        Render.rect(trackX, thumbY, 3f, thumbH, thumbColor, 1.5f)
    }

    /** Call before the field's click path. Null doesn't mean unhandled - check [consumedClick]. */
    fun mouseClicked(mouseX: Float, mouseY: Float): String? {
        consumedClick = false
        if (!isOpen) return null

        val listH = listHeight()
        val lx = bounds.x
        val ly = bounds.y
        val lw = bounds.w
        val mx = mouseX
        val my = mouseY
        if (mx !in lx..(lx + lw) || my !in ly..(ly + listH)) return null

        consumedClick = true
        val maxScroll = maxScroll()
        if (maxScroll > 0f && mx >= lx + lw - 8f) {
            grabScrollbar(my, ly, listH, maxScroll)
            return null
        }

        val scrollOffset = scrollGlide.get(scrollTarget).coerceIn(0f, maxScroll)
        val index = ((my - ly + scrollOffset) / ITEM_H).toInt()
        return if (index in items.indices) accept(index) else null
    }

    var consumedClick: Boolean = false
        private set

    private fun grabScrollbar(my: Float, ly: Float, listH: Float, maxScroll: Float) {
        val trackY = ly + 2f
        val trackH = listH - 4f
        val thumbH = thumbHeight(listH)
        val scrollOffset = scrollGlide.get(scrollTarget).coerceIn(0f, maxScroll)
        val thumbY = trackY + (scrollOffset / maxScroll) * (trackH - thumbH)

        // Jump first so the following drag starts from the new thumb position.
        if (my < thumbY || my > thumbY + thumbH) {
            val ratio = ((my - trackY - thumbH / 2f) / (trackH - thumbH)).coerceIn(0f, 1f)
            scrollTarget = ratio * maxScroll
            scrollGlide.set(scrollTarget)
        }
        draggingThumb = true
        dragStartY = my
        dragStartScroll = scrollTarget
    }

    fun mouseDragged(mouseY: Float): Boolean {
        if (!draggingThumb || !isOpen) return false
        val maxScroll = maxScroll()
        if (maxScroll <= 0f) return true

        val listH = listHeight()
        val trackScrollable = (listH - 4f) - thumbHeight(listH)
        if (trackScrollable > 0f) {
            val moved = mouseY - dragStartY
            scrollTarget = (dragStartScroll + moved / trackScrollable * maxScroll).coerceIn(0f, maxScroll)
            scrollGlide.set(scrollTarget)
        }
        return true
    }

    fun mouseReleased(): Boolean {
        if (!draggingThumb) return false
        draggingThumb = false
        return true
    }

    fun mouseScrolled(wheel: MouseScrollInput): Boolean {
        if (!isOpen) return false
        val maxScroll = maxScroll()
        if (maxScroll <= 0f) return false

        val listH = listHeight()
        if (wheel.x !in bounds.x..(bounds.x + bounds.w)) return false
        if (wheel.y !in bounds.y..(bounds.y + listH)) return false

        scrollTarget = (scrollTarget - wheel.amount * ITEM_H * 1.5f).coerceIn(0f, maxScroll)
        return true
    }

    /** Wraps and keeps it in view. */
    fun moveSelection(step: Int) {
        if (!isOpen) return
        selected = when {
            selected < 0 -> if (step > 0) 0 else items.lastIndex
            else -> (selected + step + items.size) % items.size
        }
        scrollToSelected()
    }

    fun acceptHighlighted(orSingle: Boolean): String? = when {
        !isOpen -> null
        selected >= 0 -> accept(selected)
        orSingle && items.size == 1 -> accept(0)
        else -> null
    }

    private fun accept(index: Int): String? {
        if (index !in items.indices) return null
        val chosen = items[index]
        dismissed = true
        selected = -1
        scrollTarget = 0f
        openAnim.animateTo(0f)
        return chosen
    }

    private fun scrollToSelected() {
        val maxScroll = maxScroll()
        if (selected < 0 || maxScroll <= 0f) return
        val listH = listHeight()
        val top = selected * ITEM_H
        val bottom = top + ITEM_H
        val current = scrollGlide.get(scrollTarget)
        if (top < current) {
            scrollTarget = top.coerceIn(0f, maxScroll)
        } else if (bottom > current + listH) {
            scrollTarget = (bottom - listH).coerceIn(0f, maxScroll)
        }
    }

    private fun listHeight(): Float = minOf(items.size, MAX_VISIBLE) * ITEM_H

    private fun maxScroll(): Float = (items.size * ITEM_H - listHeight()).coerceAtLeast(0f)

    private fun thumbHeight(listH: Float): Float =
        (listH / (items.size * ITEM_H) * (listH - 4f)).coerceAtLeast(14f)

    private companion object {
        const val ITEM_H = 20f
        const val MAX_VISIBLE = 5
        val POPUP_BG = Color(16, 18, 24, 255)
    }
}
