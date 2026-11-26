package org.blackaddons.core.features.impl.ui.inventorybuttons

import org.blackaddons.api.gui.GuiBox
import org.blackaddons.api.ui.anim.Animation
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.util.expSmoothing
import org.blackaddons.api.util.lerp
import java.awt.Color
import kotlin.math.min

internal class IconDrag(val raw: String, private var x: Float, private var y: Float) {

    private val fromX = x
    private val fromY = y
    private val lift = Animation().also { it.animateTo(1f) }
    private var landing: Animation? = null
    private var landFromX = 0f
    private var landFromY = 0f
    private var onto: GuiBox? = null

    val released: Boolean get() = landing != null

    // onto is the button it lands in; null sends it back to its slot.
    fun release(onto: GuiBox?) {
        this.onto = onto
        landFromX = x
        landFromY = y
        landing = Animation(durationMs = LAND_MS).also { it.animateTo(1f) }
    }

    // False once it has finished landing.
    fun draw(dt: Float, mouseX: Float, mouseY: Float): Boolean {
        val lifted = ITEM_SIZE * (1f + LIFT_GROW * lift.get())
        val landing = landing
        val cx: Float
        val cy: Float
        val size: Float
        val alpha: Float
        if (landing == null) {
            val follow = expSmoothing(dt.toDouble(), FOLLOW_RATE).toFloat()
            x += (mouseX - x) * follow
            y += (mouseY - y) * follow
            cx = x
            cy = y
            size = lifted
            alpha = 1f
        } else {
            val t = landing.get()
            if (t >= 1f) return false
            val onto = onto
            cx = lerp(landFromX, onto?.let { it.x + it.w / 2f } ?: fromX, t)
            cy = lerp(landFromY, onto?.let { it.y + it.h / 2f } ?: fromY, t)
            size = lerp(lifted, onto?.let { min(it.w, it.h) - ButtonPainter.ICON_INSET * 2f } ?: ITEM_SIZE, t)
            alpha = 1f - t
        }
        val left = cx - size / 2f
        val top = cy - size / 2f
        Render.globalAlpha(alpha)
        try {
            Render.dropShadow(left, top, size, size, SHADOW_BLUR, 0f, ButtonPainter.radius, Color(0, 0, 0, SHADOW_ALPHA))
            Render.itemSpec(raw, left, top, size, size)
        } finally {
            Render.globalAlpha(1f)
        }
        return true
    }

    private companion object {
        const val ITEM_SIZE = 16f
        const val LIFT_GROW = 0.25f
        const val FOLLOW_RATE = 28.0
        const val LAND_MS = 180L
        const val SHADOW_BLUR = 6f
        const val SHADOW_ALPHA = 110
    }
}
