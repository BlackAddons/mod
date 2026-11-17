package org.blackaddons.api.ui.widget

import org.blackaddons.api.ui.anim.DecayAnimation
import org.blackaddons.api.ui.layout.Rect
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha

/**
 * Scrolling viewport shared by every surface: offset, wheel easing and a draggable bar.
 * Call [update] before the content and [draw] after, or [render] for both:
 * ```
 * scroll.render(x, y, w, h, contentHeight) { offset -> content.draw(x, y - offset) }
 * ```
 */
class Scroll : InputTarget {

    /** Rests at the end (chat-like). New content keeps the view at the end only if it was already there. */
    var stickToEnd = false

    /** Off where a well would read as a seam. */
    var showTrack = true

    /** Clearance above a rounded bottom edge. */
    var trackBottomInset = 0f

    var maxScroll = 0f
        private set

    /** Drawn offset; hit-testing uses this. */
    var offset = 0f
        private set

    /** Where the content is heading. Use it for decisions like "am I at the end". */
    var targetOffset = 0f
        private set

    private val glide = DecayAnimation()

    private val view = Rect()
    private val track = Rect()
    private val thumb = Rect()
    private var grabOffset = 0f

    /** Set by [reset]: next frame snaps instead of gliding. */
    private var snapNext = false

    var dragging = false
        private set

    /** Call every frame before the content; returns the offset to draw at. */
    fun update(x: Float, y: Float, w: Float, h: Float, contentHeight: Float): Float {
        val hadRoom = maxScroll > 0f
        val wasAtEnd = stickToEnd && (!hadRoom || targetOffset >= maxScroll - END_SLACK)
        view.set(x, y, w, h)
        maxScroll = (contentHeight - h).coerceAtLeast(0f)

        // Stay at the end if there, otherwise keep the reader's position.
        targetOffset = if (wasAtEnd) maxScroll else targetOffset.coerceIn(0f, maxScroll)

        offset = when {
            dragging -> targetOffset
            // A reset or the first scrollable content snaps instead of gliding past the reader.
            snapNext || (wasAtEnd && !hadRoom) -> targetOffset.also { glide.set(it); snapNext = false }
            else -> glide.get(targetOffset)
        }
        layout()
        return offset
    }

    /** [update], content under a scissor, then [draw]. */
    inline fun render(x: Float, y: Float, w: Float, h: Float, contentHeight: Float, content: (offset: Float) -> Unit) {
        val offset = update(x, y, w, h, contentHeight)
        Render.pushScissor(x, y, w, h)
        content(offset)
        Render.popScissor()
        draw()
    }

    /** Nothing drawn when the content fits. */
    fun draw() {
        if (maxScroll <= 0f || track.h <= 0f) return
        val barX = view.x + view.w - INSET
        if (showTrack) Render.rect(barX, track.y, WIDTH, track.h, Theme.SURFACE.withAlpha(80), WIDTH / 2f)
        Render.rect(barX, thumb.y, WIDTH, thumb.h, if (dragging) Theme.ACCENT else Theme.BORDER, WIDTH / 2f)
    }

    /** Only over this viewport with room to scroll. */
    override fun mouseScrolled(wheel: MouseScrollInput): Boolean {
        if (maxScroll <= 0f || !view.has(wheel.x, wheel.y)) return false
        return scrollBy(wheel.amount)
    }

    /** Without hit-testing, for overlays whose trigger owns the pointer. */
    fun scrollBy(amount: Float): Boolean {
        if (maxScroll <= 0f) return false
        targetOffset = (targetOffset - amount * Theme.SCROLL_STEP).coerceIn(0f, maxScroll)
        return true
    }

    /** Eases to [target]; the next [update] clamps it to the content. */
    fun scrollTo(target: Float) {
        targetOffset = target.coerceAtLeast(0f)
    }

    /** Thumb press drags; track press jumps there centered and keeps dragging. */
    override fun mouseClicked(mouse: MouseButtonInput): Boolean {
        if (!mouse.isLeft || maxScroll <= 0f) return false
        if (thumb.has(mouse.x, mouse.y)) {
            dragging = true
            grabOffset = mouse.y - thumb.y
            return true
        }
        if (!track.has(mouse.x, mouse.y)) return false
        dragging = true
        grabOffset = thumb.h / 2f
        moveTo(mouse.y - grabOffset)
        return true
    }

    override fun mouseDragged(drag: MouseDragInput): Boolean {
        if (!dragging) return false
        moveTo(drag.y - grabOffset)
        return true
    }

    override fun mouseReleased(mouse: MouseButtonInput): Boolean {
        if (!dragging) return false
        dragging = false
        // So releasing doesn't spring.
        glide.set(offset)
        return true
    }

    /**
     * Call when [delta] of content appeared above the view, like older chat lines. The view moves along right away, so
     * what is on screen stays in place. Call it before the [update] that measures the new content.
     */
    fun shift(delta: Float) {
        if (delta == 0f) return
        maxScroll += delta
        targetOffset += delta
        offset += delta
        glide.set(offset)
    }

    /** Top, or the end with [stickToEnd]. */
    fun reset() {
        targetOffset = if (stickToEnd) maxScroll else 0f
        offset = targetOffset
        glide.set(targetOffset)
        // The end is only known next frame, since content usually changes with a reset.
        snapNext = true
    }

    /** Pure, so it's testable without a renderer. Hit area is wider than the 3px bar. */
    internal fun layout() {
        if (maxScroll <= 0f) {
            track.clear()
            thumb.clear()
            return
        }
        val barX = view.x + view.w - INSET
        val bottomInset = trackBottomInset.coerceIn(0f, view.h)
        val trackH = view.h - bottomInset
        if (trackH <= 0f) {
            track.clear()
            thumb.clear()
            return
        }
        val thumbH = (trackH * view.h / (view.h + maxScroll)).coerceAtLeast(MIN_THUMB).coerceAtMost(trackH)
        val thumbY = view.y + (trackH - thumbH) * (offset / maxScroll).coerceIn(0f, 1f)
        track.set(barX - GRAB_PAD, view.y, WIDTH + GRAB_PAD * 2f, trackH)
        thumb.set(barX - GRAB_PAD, thumbY, WIDTH + GRAB_PAD * 2f, thumbH)
    }

    /** Snapped, not eased: content must stay in sync with the thumb under the cursor. */
    private fun moveTo(thumbTop: Float) {
        val travel = track.h - thumb.h
        if (travel <= 0f) return
        targetOffset = maxScroll * ((thumbTop - track.y) / travel).coerceIn(0f, 1f)
        offset = targetOffset
        glide.set(targetOffset)
        layout()
    }

    companion object {
        /** Public so surfaces sharing the lane (sidebar tab tick) measure from it. */
        const val LANE_WIDTH = 4f

        private const val WIDTH = 3f
        private const val INSET = LANE_WIDTH
        private const val MIN_THUMB = 20f

        /** Grab margin each side of the bar. */
        private const val GRAB_PAD = 3f

        /** Float drift slack for "at the end". */
        private const val END_SLACK = 0.5f
    }
}
