package org.blackaddons.api.ui.widget

import org.blackaddons.api.ui.anim.Animation
import org.blackaddons.api.ui.anim.DecayAnimation
import org.blackaddons.api.ui.anim.Easing
import org.blackaddons.api.ui.layout.Rect
import org.blackaddons.api.ui.layout.centered
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.ScrollableList.Companion.SIDE_INSET
import org.blackaddons.api.util.FrameClock
import org.blackaddons.api.util.color.lerpColor
import kotlin.time.Duration.Companion.milliseconds

/**
 * Scrollable list of fixed-height rows that culls offscreen rows and shows [emptyMessage] when empty.
 * Reassign [items] whenever the source changes. The caller handles clicks via [rowAt]/[rowTop]. With
 * [onReorder], rows drag by their [grip] and [onReorder] fires once on release.
 */
class ScrollableList<T> : Widget {

    var items: List<T> = emptyList()

    /** [rowY] includes scrolling; [rowW] excludes the scrollbar gutter. */
    var drawRow: (item: T, index: Int, rowX: Float, rowY: Float, rowW: Float) -> Unit =
        { _, _, _, _, _ -> }

    var rowHeight: Float = 22f
    var rowGap: Float = 2f
    var emptyMessage: String? = null

    /** Off when embedding in an already-styled area. */
    var drawBackground: Boolean = true

    /** Inside the clip, above the first and below the last row, on top of the [EDGE_GAP] a [drawBackground] box adds. */
    var contentInsetY: Float = 0f

    /** For outlines that reach left of the row. */
    var leftClipBleed: Float = 0f

    /** Called on release; the owner moves the item (see [moveItem]). */
    var onReorder: ((from: Int, to: Int) -> Unit)? = null

    /** Others still make room for a moving row. */
    var canDrag: (index: Int) -> Boolean = { true }

    /** Row identity across frames for the glide. Defaults to the item by identity. */
    var key: (item: T) -> Any = { Identity(it) }

    /** Used by [draw]; [render] takes an explicit box. */
    var boxWidth: Float = 0f
    var boxHeight: Float = 0f
    override val width: Float get() = boxWidth
    override val height: Float get() = boxHeight

    /** Drawn offset; hit-testing uses it so clicks mid-glide land on the visible row. */
    var scrollOffset: Float = 0f
        private set

    private var scrollTarget: Float = 0f
    private val glide = DecayAnimation()
    private val scrollbarHover = Animation(durationMs = Theme.ANIM_HOVER.toLong())

    private var maxScroll: Float = 0f
    private val bounds = Rect()

    private var isDraggingThumb = false
    private var dragStartY = 0f
    private var dragStartScroll = 0f

    private val step get() = rowHeight + rowGap

    // A list in its own box keeps rows as far from the box's edges as from the scrollbar. Embedded, it mirrors the gutter.
    private val leftInset get() = if (drawBackground) EDGE_GAP else SIDE_INSET
    private val insetY get() = contentInsetY + if (drawBackground) EDGE_GAP else 0f

    // A drag runs from grip press to release; the list only changes on release.
    private val grips = ArrayList<Pair<Int, Rect>>()
    private val slides = HashMap<Any, Animation>()
    private var dragFrom = -1
    private var dropAt = -1
    private var grabOffset = 0f
    private val frameClock = FrameClock(cap = MAX_FRAME_MS.milliseconds)

    fun isDragged(index: Int): Boolean = index == dragFrom

    override fun draw(x: Float, y: Float) = render(x, y, width, height)

    fun render(x: Float, y: Float, w: Float, h: Float) {
        bounds.set(x, y, w, h)
        if (drawBackground) Render.rect(x, y, w, h, Theme.SURFACE.withAlpha(120), Theme.RADIUS_SMALL)

        maxScroll = (items.size * step + insetY * 2f - h).takeIf { it > OVERFLOW_SLACK } ?: 0f
        scrollTarget = scrollTarget.coerceIn(0f, maxScroll)
        scrollOffset = if (isDraggingThumb) scrollTarget else glide.get(scrollTarget)

        if (items.isEmpty()) {
            val msg = emptyMessage ?: return
            val mw = Render.textWidth(msg, Theme.FONT_SMALL, Fonts.regular)
            Render.text(
                msg,
                centered(x, w, mw),
                Render.centerY(y, h, TextStyle.SMALL_MUTED),
                TextStyle.SMALL_MUTED,
            )
            return
        }

        // A boxed list hands the bar's lane back while it doesn't scroll, so both sides match. Embedded, it stays reserved.
        val rightInset = if (drawBackground && maxScroll <= 0f) EDGE_GAP else SIDE_INSET
        val rowX = x + leftInset
        val rowW = w - leftInset - rightInset
        val leftBleed = leftClipBleed.coerceAtLeast(0f)
        val top = y + insetY - scrollOffset
        grips.clear()
        Render.pushScissor(x - leftBleed, y, w + leftBleed, h)
        if (onReorder == null) {
            var ry = top
            for ((i, item) in items.withIndex()) {
                if (ry + rowHeight >= y && ry <= y + h) drawRow(item, i, rowX, ry, rowW)
                ry += step
            }
        } else {
            drawReorderable(rowX, rowW, top, y, h)
        }
        Render.popScissor()

        drawScrollbar(x, y, w, h)
    }

    private fun drawReorderable(rowX: Float, rowW: Float, top: Float, y: Float, h: Float) {
        if (dragFrom !in items.indices) dragFrom = -1
        if (dragFrom >= 0) {
            autoScroll(y, h)
            val dragTop = (UiMouse.y - grabOffset).coerceIn(y, y + h - rowHeight)
            dropAt = ((dragTop + rowHeight / 2f - top) / step).toInt().coerceIn(0, items.lastIndex)
        }
        if (slides.size > items.size * 2 + PRUNE_SLACK) {
            val live = items.mapTo(HashSet(), key)
            slides.keys.retainAll(live)
        }
        for ((i, item) in items.withIndex()) {
            if (i == dragFrom) continue
            val ry = top + slideOf(item, slotOf(i) * step).get()
            if (ry + rowHeight >= y && ry <= y + h) drawRow(item, i, rowX, ry, rowW)
        }
        if (dragFrom >= 0) {
            // Drawn last over the rows it displaces; its glide is held so the release settles from here.
            val item = items[dragFrom]
            val ry = (UiMouse.y - grabOffset).coerceIn(y, y + h - rowHeight)
            slideOf(item, ry - top).set(ry - top)
            drawRow(item, dragFrom, rowX, ry, rowW)
            Render.hollowRect(rowX, ry, rowW, rowHeight, 1.5f, Theme.ACCENT, Theme.RADIUS_SMALL)
        }
    }

    // Shifted one slot toward the gap when between the gap and the drop point.
    private fun slotOf(index: Int): Int = when {
        dragFrom < 0 -> index
        dragFrom < dropAt && index in (dragFrom + 1)..dropAt -> index - 1
        dragFrom > dropAt && index in dropAt until dragFrom -> index + 1
        else -> index
    }

    private fun slideOf(item: T, slotY: Float): Animation {
        val slide = slides.getOrPut(key(item)) {
            Animation(initial = slotY, durationMs = Theme.ANIM_FAST.toLong(), easing = Easing.easeOutCubic)
        }
        if (slide.target != slotY) slide.animateTo(slotY)
        return slide
    }

    // Holding near an edge scrolls, faster closer to the edge.
    private fun autoScroll(y: Float, h: Float) {
        val dt = frameClock.tick().toFloat()
        val depth = when {
            UiMouse.y < y + AUTO_SCROLL_EDGE -> UiMouse.y - (y + AUTO_SCROLL_EDGE)
            UiMouse.y > y + h - AUTO_SCROLL_EDGE -> UiMouse.y - (y + h - AUTO_SCROLL_EDGE)
            else -> return
        }
        val speed = (depth / AUTO_SCROLL_EDGE).coerceIn(-1f, 1f) * AUTO_SCROLL_SPEED
        scrollTarget = (scrollTarget + speed * dt).coerceIn(0f, maxScroll)
    }

    /**
     * Draws row [index]'s grip at its top edge; call from [drawRow]. Draws nothing unless [onReorder] is set
     * and [canDrag] allows it. Returns hover.
     */
    fun grip(index: Int, x: Float, y: Float): Boolean {
        if (onReorder == null || !canDrag(index)) return false
        val area = Rect().apply { set(x, y, GRIP_W, rowHeight) }
        grips += index to area
        val mx = UiMouse.x.toDouble()
        val my = UiMouse.y.toDouble()
        val hovered = area.has(mx, my) && bounds.has(mx, my)
        val color = if (hovered || index == dragFrom) Theme.ACCENT else Theme.TEXT_MUTED
        val dotsTop = centered(y, rowHeight, GRIP_DOTS_H)
        for (row in 0..2) for (col in 0..1) {
            Render.rect(x + GRIP_INSET + col * 5f, dotsTop + row * 4f, 2f, 2f, color, 1f)
        }
        return hovered
    }

    private fun drawScrollbar(x: Float, y: Float, w: Float, h: Float) {
        if (maxScroll <= 0f) return
        val barX = x + w - BAR_LANE
        val thumbH = thumbHeight()
        val trackH = trackSpan - thumbH
        val thumbY = if (trackH > 0f) trackTop + trackH * (scrollOffset / maxScroll) else trackTop

        val hovered = UiMouse.x in (barX - 4f)..(x + w) && UiMouse.y in y..(y + h)
        scrollbarHover.animateTo(if (isDraggingThumb || hovered) 1f else 0f)
        val t = scrollbarHover.get()
        val thumbColor = lerpColor(Theme.BORDER, Theme.TEXT_SECONDARY, t)

        Render.rect(barX, trackTop, 3f, trackSpan, Theme.SURFACE.withAlpha(80), 1.5f)
        Render.rect(barX, thumbY, 3f, thumbH, thumbColor, 1.5f)
    }

    // Beside the rows only, so the bar keeps the same clearance from the box's ends as they do.
    private val trackTop get() = bounds.y + insetY
    private val trackSpan get() = (bounds.h - insetY * 2f).coerceAtLeast(0f)

    private fun thumbHeight() = (trackSpan * bounds.h / (bounds.h + maxScroll)).coerceAtLeast(20f)

    fun rowTop(index: Int): Float = bounds.y + insetY - scrollOffset + index * step

    /** -1 if none; gaps and the scrollbar don't count. */
    fun rowAt(mouseX: Float, mouseY: Float): Int {
        if (!bounds.has(mouseX, mouseY)) return -1
        if (maxScroll > 0f && mouseX >= bounds.x + bounds.w - SIDE_INSET) return -1
        var ry = bounds.y + insetY - scrollOffset
        for (i in items.indices) {
            if (mouseY >= ry && mouseY <= ry + rowHeight) return i
            ry += step
        }
        return -1
    }

    override fun mouseClicked(mouse: MouseButtonInput): Boolean {
        if (mouse.isLeft && onReorder != null && bounds.has(mouse.x, mouse.y)) {
            val grabbed = grips.lastOrNull { it.second.has(mouse.x, mouse.y) }
            if (grabbed != null) {
                dragFrom = grabbed.first
                dropAt = grabbed.first
                grabOffset = mouse.y - grabbed.second.y
                frameClock.reset()
                return true
            }
        }
        if (!mouse.isLeft || maxScroll <= 0f || !bounds.has(mouse.x, mouse.y)) return false

        val barX = bounds.x + bounds.w - 10f
        if (mouse.x >= barX && mouse.x <= bounds.x + bounds.w) {
            val thumbH = thumbHeight()
            val trackH = trackSpan - thumbH
            val thumbY = if (trackH > 0f) trackTop + trackH * (scrollOffset / maxScroll) else trackTop

            if (mouse.y >= thumbY && mouse.y <= thumbY + thumbH) {
                isDraggingThumb = true
                dragStartY = mouse.y
                dragStartScroll = scrollTarget
            } else if (trackH > 0f) {
                val clickRatio = ((mouse.y - trackTop - thumbH / 2f) / trackH).coerceIn(0f, 1f)
                val newScroll = clickRatio * maxScroll
                scrollTarget = newScroll
                scrollOffset = newScroll
                glide.set(newScroll)
                isDraggingThumb = true
                dragStartY = mouse.y
                dragStartScroll = newScroll
            }
            return true
        }
        return false
    }

    override fun mouseDragged(drag: MouseDragInput): Boolean {
        // The row follows UiMouse, so it keeps up while the list scrolls under a still cursor.
        if (dragFrom >= 0) return true
        if (isDraggingThumb && maxScroll > 0f) {
            val trackH = trackSpan - thumbHeight()
            if (trackH > 0f) {
                val deltaPixels = drag.y - dragStartY
                val newScroll = (dragStartScroll + deltaPixels / trackH * maxScroll).coerceIn(0f, maxScroll)
                scrollTarget = newScroll
                scrollOffset = newScroll
                glide.set(newScroll)
            }
            return true
        }
        return false
    }

    override fun mouseReleased(mouse: MouseButtonInput): Boolean {
        if (dragFrom >= 0) {
            val from = dragFrom
            val to = dropAt
            dragFrom = -1
            dropAt = -1
            if (from != to && from in items.indices && to in items.indices) onReorder?.invoke(from, to)
            return true
        }
        if (isDraggingThumb) {
            isDraggingThumb = false
            return true
        }
        return false
    }

    override fun mouseScrolled(wheel: MouseScrollInput): Boolean {
        if (maxScroll <= 0f || !bounds.has(wheel.x, wheel.y)) return false
        scrollTarget = (scrollTarget - wheel.amount * Theme.SCROLL_STEP).coerceIn(0f, maxScroll)
        return true
    }

    fun resetScroll() {
        scrollTarget = 0f
        scrollOffset = 0f
        glide.set(0f)
        isDraggingThumb = false
    }

    fun clear() {
        bounds.clear()
        isDraggingThumb = false
        dragFrom = -1
    }

    companion object {
        /** The scrollbar gutter, reserved even when hidden. */
        const val SIDE_INSET = 8f

        /** The bar and its clearance from the box edge, at the right of the gutter. */
        private const val BAR_LANE = 4f

        /** A row's clearance from the scrollbar, and in a [drawBackground] list from the box's other edges too. */
        const val EDGE_GAP = SIDE_INSET - BAR_LANE

        /** Float noise from a list sized to fit its rows. */
        private const val OVERFLOW_SLACK = 0.5f

        /** Wider than the dots so it's clickable. */
        const val GRIP_W = 24f
        private const val GRIP_INSET = 2f
        private const val GRIP_DOTS_H = 10f
        private const val AUTO_SCROLL_EDGE = 24f
        private const val AUTO_SCROLL_SPEED = 600f
        private const val MAX_FRAME_MS = 50L
        private const val PRUNE_SLACK = 16
    }

    private class Identity(val value: Any?) {
        override fun equals(other: Any?): Boolean = other is Identity && other.value === value
        override fun hashCode(): Int = System.identityHashCode(value)
    }
}

/** Indices as [ScrollableList.onReorder] reports them. */
fun <T> MutableList<T>.moveItem(from: Int, to: Int) {
    if (from == to || from !in indices || to !in indices) return
    add(to, removeAt(from))
}
