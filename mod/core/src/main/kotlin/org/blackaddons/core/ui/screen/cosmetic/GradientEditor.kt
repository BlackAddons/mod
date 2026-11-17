package org.blackaddons.core.ui.screen.cosmetic

import org.blackaddons.api.ui.layout.Rect
import org.blackaddons.api.ui.render.GradientDir
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.Panel
import org.blackaddons.api.ui.screen.color.ColorPopover
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.InputTarget
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.MouseDragInput
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.color.ColorStop
import org.blackaddons.core.cosmetics.GradientStops
import org.blackaddons.core.cosmetics.me.CosmeticDraft
import java.awt.Color
import kotlin.math.abs
import kotlin.math.roundToInt

private val tr = I18n.scope("blackaddons.cosmetic_editor")

/**
 * Gradient mode controls: a bar with a point per stop, then the per-character/animated toggles. Click a point to color
 * it, drag to move, click the bar to add, pull down to remove, same as the website editor. Speed is shared with Chroma,
 * so [ColorModeEditor] owns it.
 *
 * Stops are edited in place and stay unsorted during a drag, so an index stays under the pointer. Saving spreads stops
 * that are at the same place ([GradientStops.strictlyAscending]).
 */
internal class GradientEditor(
    private val draft: () -> CosmeticDraft?,
    private val openPopover: (Panel) -> Unit,
    private val onEdit: () -> Unit,
) : InputTarget {

    private val bar = Rect()
    private val perCharToggle = Rect()
    private val animatedToggle = Rect()

    private var selected = -1

    // Press state: drag vs click, and whether it was pulled far enough to remove on release.
    private var pressed = -1
    private var pressX = 0f
    private var moved = false
    private var removing = false

    fun render(x: Float, startY: Float, w: Float, d: CosmeticDraft, mouseX: Float, mouseY: Float): Float {
        var cy = startY
        val labelH = Render.textHeight(Theme.FONT_SMALL, Fonts.regular)
        Render.text(tr("stops", "Gradient stops"), x, cy, TextStyle.SMALL_SECONDARY)
        val count = "${d.stops.size} / ${GradientStops.MAX_STOPS}"
        Render.textRight(count, x + w, cy, TextStyle.SMALL_MUTED)
        cy += labelH + 6f

        // Inset by a handle radius, so end points stay whole.
        bar.set(x + HANDLE_R, cy + HANDLE_R - BAR_H / 2f + 1f, w - HANDLE_R * 2f, BAR_H)
        drawBar(d.stops)
        drawHandles(d.stops, mouseX, mouseY)
        cy += HANDLE_R * 2f + 8f

        // One line, because a wrapped legend leaves a fragment. If even the short text is too wide: cut it, full text on hover.
        val fullHint = tr("stops_hint", "Click: color · Drag: move · Click bar: add · Pull down: remove")
        Render.text(fullHint, x, cy, TextStyle.SMALL_MUTED, maxWidth = w)
        cy += labelH + 10f

        perCharToggle.set(x, cy, 28f, 14f)
        Render.toggleSwitch(perCharToggle, if (d.perCharacter) 1f else 0f)
        val perCharHint = if (d.perCharacter)
            tr("per_char_on", "Per-character (whole name one color)")
        else tr("per_char_off", "Per-character (spread across letters)")
        Render.text(perCharHint, perCharToggle.x + perCharToggle.w + 8f, Render.centerY(cy, 14f, TextStyle.SMALL_SECONDARY), TextStyle.SMALL_SECONDARY)
        cy += 20f

        animatedToggle.set(x, cy, 28f, 14f)
        Render.toggleSwitch(animatedToggle, if (d.animated) 1f else 0f)
        Render.text(tr("animated", "Animated"), animatedToggle.x + animatedToggle.w + 8f, Render.centerY(cy, 14f, TextStyle.SMALL_SECONDARY), TextStyle.SMALL_SECONDARY)
        cy += 22f

        return cy
    }

    private fun drawBar(stops: List<ColorStop>) {
        val sorted = stops.sortedBy { it.fraction }
        Render.rect(bar, Theme.SURFACE, BAR_RADIUS)
        if (sorted.isNotEmpty()) {
            // Square segments, clipped before the rounded ends. Flat outside the first and last stop.
            Render.pushScissor(bar.x + BAR_RADIUS, bar.y, bar.w - BAR_RADIUS * 2f, bar.h)
            var from = bar.x
            var fromColor = rgb(sorted.first())
            for (stop in sorted) {
                val to = bar.x + bar.w * stop.fraction
                if (to > from) Render.gradientRect(from, bar.y, to - from + 0.5f, bar.h, fromColor, rgb(stop), GradientDir.LEFT_RIGHT)
                from = to
                fromColor = rgb(stop)
            }
            if (bar.x + bar.w > from) Render.rect(from, bar.y, bar.x + bar.w - from, bar.h, fromColor)
            Render.popScissor()

            // Ends are short rounded sweeps cut to the corner width. Only off by a sliver if a stop is inside it.
            val reach = BAR_RADIUS * 2f / bar.w
            endCap(bar.x, colorAt(stops, 0f), colorAt(stops, reach), cutLeft = true)
            endCap(bar.x + bar.w - BAR_RADIUS * 2f, colorAt(stops, 1f - reach), colorAt(stops, 1f), cutLeft = false)
        }
        Render.hollowRect(bar, 1f, Theme.BORDER, BAR_RADIUS)
    }

    private fun endCap(x: Float, from: Int, to: Int, cutLeft: Boolean) {
        Render.pushScissor(if (cutLeft) x else x + BAR_RADIUS, bar.y, BAR_RADIUS, bar.h)
        Render.gradientRect(x, bar.y, BAR_RADIUS * 2f, bar.h, Color(from), Color(to), GradientDir.LEFT_RIGHT, BAR_RADIUS)
        Render.popScissor()
    }

    private fun drawHandles(stops: List<ColorStop>, mouseX: Float, mouseY: Float) {
        val hovered = handleAt(stops, mouseX, mouseY)
        val order = stops.indices.sortedBy { it == selected }
        for (i in order) {
            val cx = handleX(stops[i])
            val cy = bar.y + bar.h / 2f
            val leaving = removing && i == pressed
            val r = if (leaving) HANDLE_R * 0.7f else HANDLE_R
            val alpha = if (leaving) 0.4f else 1f
            if (i == selected) Render.circle(cx, cy, r + 2f, Theme.ACCENT.withAlpha(alpha))
            Render.circle(cx, cy, r, (if (i == hovered || i == pressed) Color.WHITE else Color(230, 230, 235)).withAlpha(alpha))
            Render.circle(cx, cy, r - 2f, rgb(stops[i]).withAlpha(alpha))
        }
    }

    override fun mouseClicked(mouse: MouseButtonInput): Boolean {
        val d = draft() ?: return false
        val mx = mouse.x
        val my = mouse.y
        handleAt(d.stops, mx, my)?.let { i ->
            selected = i
            pressed = i
            pressX = mx
            moved = false
            removing = false
            return true
        }
        if (bar.has(mx, my) && d.stops.size < GradientStops.MAX_STOPS) {
            val fraction = snap(fractionAt(mx))
            d.stops.add(ColorStop(colorAt(d.stops, fraction), fraction))
            onEdit()
            // Held right away, so the press can drag the new point. Counts as moved, so release doesn't open a picker.
            selected = d.stops.lastIndex
            pressed = selected
            pressX = mx
            moved = true
            removing = false
            return true
        }
        if (perCharToggle.has(mx, my)) { d.perCharacter = !d.perCharacter; onEdit(); return true }
        if (animatedToggle.has(mx, my)) { d.animated = !d.animated; onEdit(); return true }
        return false
    }

    override fun mouseDragged(drag: MouseDragInput): Boolean {
        val d = draft() ?: return false
        val mx = drag.x
        val my = drag.y
        if (pressed !in d.stops.indices) return false
        if (!moved && abs(mx - pressX) < DRAG_SLOP) return true
        moved = true
        d.stops[pressed] = ColorStop(d.stops[pressed].rgb, snap(fractionAt(mx)))
        removing = d.stops.size > GradientStops.MIN_STOPS && my > bar.y + bar.h + REMOVE_PULL
        onEdit()
        return true
    }

    override fun mouseReleased(mouse: MouseButtonInput): Boolean {
        val d = draft()
        val i = pressed
        pressed = -1
        if (d == null || i !in d.stops.indices) return false
        when {
            removing -> {
                d.stops.removeAt(i)
                selected = -1
                onEdit()
            }
            !moved -> openColor(d, i)
        }
        removing = false
        return true
    }

    private fun openColor(d: CosmeticDraft, i: Int) {
        val cx = handleX(d.stops[i])
        openPopover(
            ColorPopover(
                rgb(d.stops[i]),
                anchorX = cx - HANDLE_R, anchorY = bar.y + bar.h / 2f - HANDLE_R,
                anchorW = HANDLE_R * 2f, anchorH = HANDLE_R * 2f,
            ) { picked ->
                if (i in d.stops.indices) {
                    d.stops[i] = ColorStop(picked.rgb and 0xFFFFFF, d.stops[i].fraction)
                    onEdit()
                }
            },
        )
    }

    private fun handleAt(stops: List<ColorStop>, mx: Float, my: Float): Int? {
        val cy = bar.y + bar.h / 2f
        if (abs(my - cy) > HANDLE_R + HIT_SLOP) return null
        // Nearest wins. Ties go to the selected (top) point.
        return stops.indices
            .filter { abs(mx - handleX(stops[it])) <= HANDLE_R + HIT_SLOP }
            .minByOrNull { abs(mx - handleX(stops[it])) - if (it == selected) 0.01f else 0f }
    }

    private fun handleX(stop: ColorStop) = bar.x + bar.w * stop.fraction

    private fun fractionAt(mx: Float) = ((mx - bar.x) / bar.w).coerceIn(0f, 1f)

    /** The gradient's current color at [fraction], so adding a point doesn't change the look. */
    private fun colorAt(stops: List<ColorStop>, fraction: Float): Int {
        val sorted = stops.sortedBy { it.fraction }
        val left = sorted.lastOrNull { it.fraction <= fraction }
        val right = sorted.firstOrNull { it.fraction >= fraction }
        return when {
            left == null && right == null -> 0xFFFFFF
            left == null -> right!!.rgb
            right == null || right.fraction == left.fraction -> left.rgb
            else -> lerp(left.rgb, right.rgb, (fraction - left.fraction) / (right.fraction - left.fraction))
        }
    }

    private companion object {
        const val BAR_H = 14f
        const val BAR_RADIUS = 5f
        const val HANDLE_R = 6f
        const val HIT_SLOP = 2f
        const val DRAG_SLOP = 2f
        const val REMOVE_PULL = 24f

        /** Positions move in hundredths, like on the website. */
        fun snap(fraction: Float) = (fraction * 100f).roundToInt() / 100f

        fun rgb(stop: ColorStop) = Color(stop.rgb and 0xFFFFFF)

        fun lerp(a: Int, b: Int, t: Float): Int {
            fun channel(shift: Int): Int {
                val av = (a shr shift) and 0xFF
                val bv = (b shr shift) and 0xFF
                return (av + (bv - av) * t).roundToInt()
            }
            return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }
    }
}
