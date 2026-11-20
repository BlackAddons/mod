package org.blackaddons.api.ui.render

import org.blackaddons.api.feature.settings.impl.ChromaMode
import org.blackaddons.api.feature.settings.impl.ColorSetting
import org.blackaddons.api.util.color.Chroma
import org.blackaddons.api.util.color.ColorStop
import org.blackaddons.api.util.color.GradientColors
import org.blackaddons.api.util.graphemes
import java.awt.Color
import kotlin.math.cos
import kotlin.math.sin

internal object TextEffects {

    fun chromaText(
        text: String, x: Number, y: Number, size: Number,
        base: Color, speed: Float, font: Font,
        angleDeg: Float,
        scale: Float,
    ): Float {
        val rad = Math.toRadians(angleDeg.toDouble())
        val dirX = cos(rad).toFloat()
        val dirY = sin(rad).toFloat()
        val startX = x.toFloat()
        var cx = startX
        for (s in text.graphemes()) {
            val screen = Render.toScreen(cx, y)
            val offset = (screen[0] * dirX + screen[1] * dirY) * scale
            Render.text(s, cx, y, size, Chroma.color(base, speed, offset), font)
            cx += Render.textWidth(s, size, font)
        }
        return cx - startX
    }

    fun chromaText(
        text: String, x: Number, y: Number, size: Number,
        color: ColorSetting, font: Font,
        angleDeg: Float,
        scale: Float,
    ): Float {
        if (!color.chroma || color.chromaMode == ChromaMode.ALL) {
            Render.text(text, x, y, size, color.value, font)
            return Render.textWidth(text, size, font)
        }
        return chromaText(text, x, y, size, color.baseColor, color.chromaSpeed, font, angleDeg, scale)
    }

    fun gradientText(
        text: String, x: Number, y: Number, size: Number,
        stops: List<ColorStop>, font: Font,
        animated: Boolean, speed: Float, solid: Boolean,
    ): Float {
        if (stops.isEmpty()) {
            Render.text(text, x, y, size, Color.WHITE, font)
            return Render.textWidth(text, size, font)
        }
        val glyphs = text.graphemes()
        val n = glyphs.size
        val time = System.currentTimeMillis() / 1000.0
        val startX = x.toFloat()
        var cx = startX
        for (i in glyphs.indices) {
            val s = glyphs[i]
            val rgb = if (animated) {
                val offset = if (solid || n <= 1) 0f else (n - 1 - i).toFloat() / (n - 1)
                GradientColors.animatedGradientColor(stops, speed, offset, time)
            } else {
                val frac = if (solid || n <= 1) 0f else i.toFloat() / (n - 1)
                GradientColors.colorAt(stops, frac)
            }
            Render.text(s, cx, y, size, Color((0xFF shl 24) or (rgb and 0xFFFFFF), true), font)
            cx += Render.textWidth(s, size, font)
        }
        return cx - startX
    }
}
