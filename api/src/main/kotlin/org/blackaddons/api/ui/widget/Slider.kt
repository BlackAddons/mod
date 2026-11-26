package org.blackaddons.api.ui.widget

import org.blackaddons.api.ui.anim.DecayAnimation
import org.blackaddons.api.ui.layout.Rect
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.util.snapToStep
import kotlin.math.abs

/** Self-contained ClickGUI slider: owns its [value] and drag. [step] snaps (0 = free); the thumb eases. */
class Slider(
    var min: Double = 0.0,
    var max: Double = 1.0,
    value: Double = 0.0,
    var step: Double = 0.0,
    var onChange: (Double) -> Unit = {},
) : Widget {

    private val bounds = Rect()
    private val fill = DecayAnimation()
    private var dragging = false

    var enabled: Boolean = true

    /** Marked on the track - pass the setting's default. */
    var defaultValue: Double? = null

    /** Clamped and snapped; setting it fires [onChange]. */
    var value: Double = value.coerceIn(min, max)
        set(v) {
            val snapped = v.snapToStep(min, max, step)
            val changed = snapped != field
            field = snapped
            if (changed) onChange(snapped)
        }

    /** Without firing [onChange]. */
    fun sync(v: Double) {
        val saved = onChange
        onChange = {}
        try {
            value = v
        } finally {
            onChange = saved
        }
    }

    /** Height is fixed at 2x [trackHeight]. */
    var boxWidth: Float = 140f
    var trackHeight: Float = 6f

    override val width: Float get() = boxWidth
    override val height: Float get() = trackHeight * 2f

    override fun draw(x: Float, y: Float) = render(x, y, width, height, UiMouse.x, UiMouse.y)

    /** [Render.slider] centers the thumb on the track end, shadow included. */
    private val thumbInset: Float get() = trackHeight + THUMB_SHADOW

    fun render(x: Float, y: Float, w: Float, h: Float, mouseX: Float, mouseY: Float) {
        bounds.set(x, y, w, h)
        val cy = y + (h - trackHeight) / 2f
        // Keeps the thumb inside the claimed width at either end.
        val trackX = x + thumbInset
        val trackW = (w - thumbInset * 2f).coerceAtLeast(1f)
        val target = if (dragging) ((mouseX - trackX) / trackW).coerceIn(0f, 1f) else fraction()
        val t = fill.get(target)
        Render.slider(trackX, cy, trackW, trackHeight, t, active = enabled)
        drawDefaultMark(trackX, cy, trackW)
    }

    /**
     * Always drawn - hiding it near the thumb made the default hardest to hit exactly when it mattered. Works
     * with the magnet in [setFromMouse].
     */
    private fun drawDefaultMark(x: Float, cy: Float, w: Float) {
        val def = defaultValue ?: return
        if (max <= min || def < min || def > max) return
        val markX = x + w * ((def - min) / (max - min)).toFloat()
        Render.circle(markX, cy + trackHeight / 2f, MARK_RADIUS, Theme.TEXT_MUTED)
    }

    private fun fraction(): Float =
        if (max <= min) 0f else ((value - min) / (max - min)).toFloat().coerceIn(0f, 1f)


    // Maps over the track, not the hit area, so dragging to an edge lands exactly on min/max.
    private fun setFromMouse(mouseX: Float) {
        val trackW = bounds.w - thumbInset * 2f
        if (trackW <= 0f) return
        val t = ((mouseX - bounds.x - thumbInset) / trackW).coerceIn(0f, 1f)
        value = magnet(min + (max - min) * t, trackW)
    }

    /** Snaps to the default within [MAGNET_PX]. In pixels, not value, so it feels the same at every range. */
    private fun magnet(raw: Double, trackW: Float): Double {
        val def = defaultValue ?: return raw
        if (max <= min || def < min || def > max) return raw
        val perPixel = (max - min) / trackW
        return if (abs(raw - def) <= MAGNET_PX * perPixel) def else raw
    }

    override fun mouseClicked(mouse: MouseButtonInput): Boolean {
        if (!mouse.isLeft || !enabled || !bounds.has(mouse.x, mouse.y)) return false
        dragging = true
        setFromMouse(mouse.x)
        return true
    }

    override fun mouseReleased(mouse: MouseButtonInput): Boolean {
        if (mouse.isLeft && dragging) {
            dragging = false
            return true
        }
        return false
    }

    override fun mouseDragged(drag: MouseDragInput): Boolean {
        if (!dragging) return false
        setFromMouse(drag.x)
        return true
    }

    /** Ctrl+wheel only; a plain wheel scrolls the list the slider sits in. */
    override fun mouseScrolled(wheel: MouseScrollInput): Boolean {
        if (!enabled || !UiKeys.ctrl || !bounds.has(wheel.x, wheel.y)) return false
        value += (if (step > 0.0) step else (max - min) / 20.0) * wheel.amount
        return true
    }

    fun clearBounds() {
        bounds.clear()
    }

    private companion object {
        /** Matches the 1px shadow of [Render.slider]. */
        const val THUMB_SHADOW = 1f

        const val MARK_RADIUS = 1.75f

        const val MAGNET_PX = 4f
    }
}
