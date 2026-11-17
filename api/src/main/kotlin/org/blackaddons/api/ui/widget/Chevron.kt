package org.blackaddons.api.ui.widget

import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.dropdown.Dropdown
import java.awt.Color
import kotlin.math.cos
import kotlin.math.sin

/** Two-stroke vector chevron used by every collapse arrow and [Dropdown]. */
object Chevron {

    /** [progress] 0 points right, 1 down, -1 up. [size] is the arm length. */
    fun draw(cx: Float, cy: Float, size: Float, thickness: Float, progress: Float, color: Color = Theme.TEXT_MUTED) {
        val angleRad = Math.toRadians(45.0).toFloat()
        val dx = cos(angleRad.toDouble()).toFloat() * size
        val dy = sin(angleRad.toDouble()).toFloat() * size
        Render.push()
        Render.translate(cx, cy)
        Render.rotate(Math.toRadians(90.0 * progress).toFloat())
        val tipX = dx / 2f
        val wingsX = -dx / 2f
        Render.line(tipX, 0f, wingsX, -dy, thickness, color)
        Render.line(tipX, 0f, wingsX, dy, thickness, color)
        val r = thickness / 2f
        Render.circle(tipX, 0f, r, color)
        Render.circle(wingsX, -dy, r, color)
        Render.circle(wingsX, dy, r, color)
        Render.pop()
    }
}
