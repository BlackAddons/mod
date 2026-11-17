package org.blackaddons.api.ui.layout

import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.widget.UiFrame
import org.blackaddons.api.ui.widget.UiMouse

/**
 * Hit box for immediate-mode controls, re-stamped every frame. Only the scissor-visible part takes clicks. Stamped
 * while a panel draws, it expires with the frame; stamped elsewhere (HUD), it stays until [clear].
 */
class Rect(var x: Float = 0f, var y: Float = 0f, var w: Float = 0f, var h: Float = 0f) {

    private var frame = UNTRACKED

    // Visible part after scissoring; the box itself stays whole.
    private var clipX1 = Float.NEGATIVE_INFINITY
    private var clipY1 = Float.NEGATIVE_INFINITY
    private var clipX2 = Float.POSITIVE_INFINITY
    private var clipY2 = Float.POSITIVE_INFINITY

    fun set(x: Float, y: Float, w: Float, h: Float) {
        check(!Render.recordingLayer) { "a Rect was stamped inside Render.layer, whose content does not run on replayed frames" }
        this.x = x; this.y = y; this.w = w; this.h = h
        frame = if (UiFrame.drawing) UiFrame.id else UNTRACKED
        val clip = Render.clip
        clipX1 = clip?.get(0) ?: Float.NEGATIVE_INFINITY
        clipY1 = clip?.get(1) ?: Float.NEGATIVE_INFINITY
        clipX2 = clip?.get(2) ?: Float.POSITIVE_INFINITY
        clipY2 = clip?.get(3) ?: Float.POSITIVE_INFINITY
    }

    fun clear() = set(0f, 0f, 0f, 0f)

    fun has(mx: Float, my: Float) =
        isDrawn && hit(mx, my, x, y, w, h) && mx >= clipX1 && my >= clipY1 && mx < clipX2 && my < clipY2

    /**
     * Drawn in the latest frame, or stamped outside panels. While drawing, the previous frame also counts for
     * controls checked before they're re-stamped.
     */
    val isDrawn: Boolean
        get() = frame == UNTRACKED || frame == UiFrame.id || (UiFrame.drawing && frame == UiFrame.id - 1)

    fun has(mx: Double, my: Double) = has(mx.toFloat(), my.toFloat())

    private companion object {
        const val UNTRACKED = -1L
    }
}

/**
 * The pointer is over the visible part of a box drawn in this panel frame: the scissor counts, so a row scrolled
 * out of view is never hovered. Always false outside a panel (HUD), where the pointer is not tracked.
 */
fun hovered(x: Float, y: Float, w: Float, h: Float): Boolean {
    if (!UiFrame.drawing) return false
    val mx = UiMouse.x
    val my = UiMouse.y
    val clip = Render.clip
    return hit(mx, my, x, y, w, h) &&
        (clip == null || (mx >= clip[0] && my >= clip[1] && mx < clip[2] && my < clip[3]))
}

/**
 * Start position that centers something [inner] long in the span of [size] starting at [start]: a box across a row, an
 * icon down a button. Text has its own, [Render.centerY].
 */
fun centered(start: Float, size: Float, inner: Float): Float = start + (size - inner) / 2f

/** The width of each of [count] equal columns across [width], with [gap] between them. */
fun columnWidth(width: Float, count: Int, gap: Float): Float = (width - gap * (count - 1)) / count

/** Right/bottom edges excluded, so touching boxes never share a point. */
fun hit(px: Float, py: Float, x: Float, y: Float, w: Float, h: Float): Boolean =
    px >= x && py >= y && px < x + w && py < y + h

fun hit(px: Double, py: Double, x: Float, y: Float, w: Float, h: Float): Boolean =
    hit(px.toFloat(), py.toFloat(), x, y, w, h)

/** Whole GUI pixels, as container slots use. */
fun hit(px: Int, py: Int, x: Int, y: Int, w: Int, h: Int): Boolean =
    px >= x && py >= y && px < x + w && py < y + h
