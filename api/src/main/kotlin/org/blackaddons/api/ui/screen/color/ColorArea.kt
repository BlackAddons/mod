package org.blackaddons.api.ui.screen.color
import org.blackaddons.api.ui.layout.hit
import org.blackaddons.api.ui.render.GradientDir
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.InputTarget
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.MouseDragInput
import java.awt.Color

internal class ColorArea(
    initial: Color,
    private val allowAlpha: Boolean,
    private val onChange: (Color) -> Unit,
    /** The hue strip has the same height. */
    private val svSize: Float = 220f,
) : InputTarget {
    private var h: Float
    private var s: Float
    private var v: Float
    private var a: Float

    init {
        val hsb = Color.RGBtoHSB(initial.red, initial.green, initial.blue, null)
        h = hsb[0]; s = hsb[1]; v = hsb[2]
        a = if (allowAlpha) initial.alpha / 255f else 1f
    }

    private enum class Drag { NONE, SV, HUE, ALPHA }
    private var dragging = Drag.NONE

    private var svX = 0f; private var svY = 0f
    private var hueX = 0f; private var hueY = 0f; private val hueW = 18f
    private var alphaX = 0f; private var alphaY = 0f; private var alphaW = 0f; private val alphaH = 14f

    val contentWidth: Float get() = svSize + 14f + hueW

    /** Valid after [render]. */
    var bottomY = 0f
        private set

    val color: Color
        get() {
            val rgb = Color.HSBtoRGB(h, s, v) and 0x00FFFFFF
            val alpha = if (allowAlpha) (a * 255f).toInt().coerceIn(0, 255) else 255
            return Color((alpha shl 24) or rgb, true)
        }

    /** Keeps alpha when there's no alpha channel. */
    fun setColor(c: Color) {
        val hsb = Color.RGBtoHSB(c.red, c.green, c.blue, null)
        h = hsb[0]; s = hsb[1]; v = hsb[2]
        if (allowAlpha) a = c.alpha / 255f
        onChange(color)
    }

    fun render(x: Float, y: Float) {
        svX = x; svY = y
        renderSV(svX, svY, svSize)

        hueX = svX + svSize + 14f; hueY = svY
        renderHue(hueX, hueY, hueW, svSize)

        bottomY = if (allowAlpha) {
            alphaX = svX; alphaY = svY + svSize + 12f; alphaW = contentWidth
            renderAlpha(alphaX, alphaY, alphaW, alphaH)
            alphaY + alphaH
        } else {
            svY + svSize
        }
    }

    override fun mouseClicked(mouse: MouseButtonInput): Boolean {
        if (!mouse.isLeft) return false
        val mx = mouse.x
        val my = mouse.y
        when {
            hit(mx, my, svX, svY, svSize, svSize) -> { dragging = Drag.SV; updateSV(mx, my); return true }
            hit(mx, my, hueX, hueY, hueW, svSize) -> { dragging = Drag.HUE; updateHue(my); return true }
            allowAlpha && hit(mx, my, alphaX, alphaY, alphaW, alphaH) -> { dragging = Drag.ALPHA; updateAlpha(mx); return true }
        }
        return false
    }

    override fun mouseDragged(drag: MouseDragInput): Boolean {
        val mx = drag.x
        val my = drag.y
        when (dragging) {
            Drag.SV -> { updateSV(mx, my); return true }
            Drag.HUE -> { updateHue(my); return true }
            Drag.ALPHA -> { updateAlpha(mx); return true }
            Drag.NONE -> {}
        }
        return false
    }

    override fun mouseReleased(mouse: MouseButtonInput): Boolean {
        dragging = Drag.NONE
        return false
    }

    private fun updateSV(mx: Float, my: Float) {
        s = ((mx - svX) / svSize).coerceIn(0f, 1f)
        v = (1f - (my - svY) / svSize).coerceIn(0f, 1f)
        onChange(color)
    }

    private fun updateHue(my: Float) {
        h = ((my - hueY) / svSize).coerceIn(0f, 1f)
        onChange(color)
    }

    private fun updateAlpha(mx: Float) {
        a = ((mx - alphaX) / alphaW).coerceIn(0f, 1f)
        onChange(color)
    }

    private fun renderSV(x: Float, y: Float, size: Float) {
        val hueColor = Color(Color.HSBtoRGB(h, 1f, 1f))
        Render.rect(x + 0.5f, y + 0.5f, size - 1f, size - 1f, hueColor, Theme.RADIUS_SMALL - 0.5f)
        Render.gradientRect(x, y, size, size, Color.WHITE, Color(255, 255, 255, 0), GradientDir.LEFT_RIGHT, Theme.RADIUS_SMALL)
        Render.gradientRect(x, y, size, size, Color(0, 0, 0, 0), Color.BLACK, GradientDir.TOP_BOTTOM, Theme.RADIUS_SMALL)
        Render.hollowRect(x, y, size, size, 1f, Theme.BORDER, Theme.RADIUS_SMALL)
        val cx = x + s * size
        val cy = y + (1f - v) * size
        Render.circle(cx, cy, 5f, Color(0, 0, 0, 200))
        Render.circle(cx, cy, 4f, Color.WHITE)
    }

    private fun renderHue(x: Float, y: Float, w: Float, height: Float) {
        val segments = 6
        val segH = height / segments
        val stops = listOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED)
        for (i in 0 until segments) {
            val yStart = y + i * segH
            val hSeg = if (i == segments - 1) height - (i * segH) else segH + 1f
            Render.gradientRect(x, yStart, w, hSeg, stops[i], stops[i + 1], GradientDir.TOP_BOTTOM, 0f)
        }
        val cy = y + h * height
        Render.rect(x - 2f, cy - 1.5f, w + 4f, 3f, Color.WHITE)
    }

    private fun renderAlpha(x: Float, y: Float, w: Float, hh: Float) {
        val base = Color(Color.HSBtoRGB(h, s, v))
        Render.rect(x + 0.5f, y + 0.5f, w - 1f, hh - 1f, Color(200, 200, 200), Theme.RADIUS_SMALL - 0.5f)
        Render.gradientRect(
            x, y, w, hh,
            Color(base.red, base.green, base.blue, 0),
            Color(base.red, base.green, base.blue, 255),
            GradientDir.LEFT_RIGHT, Theme.RADIUS_SMALL,
        )
        Render.hollowRect(x, y, w, hh, 1f, Theme.BORDER, Theme.RADIUS_SMALL)
        val cx = x + a * w
        Render.rect(cx - 1.5f, y - 2f, 3f, hh + 4f, Color.WHITE)
    }
}
