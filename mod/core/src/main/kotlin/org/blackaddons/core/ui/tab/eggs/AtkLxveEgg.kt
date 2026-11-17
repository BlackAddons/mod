package org.blackaddons.core.ui.tab.eggs

import org.blackaddons.api.ui.anim.isVisible
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.util.color.ColorStop
import org.blackaddons.api.util.color.GradientColors
import org.blackaddons.api.util.graphemes
import org.blackaddons.core.ui.tab.eggs.AtkLxveEgg.FLAG
import java.awt.Color
import kotlin.math.cos
import kotlin.math.sin

/**
 * Femboy flag colors across the name, plus a burst of little flags. One flag and not two, at his own
 * request.
 *
 * Pure in `progress` like every egg: chip direction and speed come from the index, gravity is progress
 * squared, so closing sucks them back in. A stripe flag needs no asset and no license.
 */
internal object AtkLxveEgg {

    /** A firework that's over in a blink was never a firework. */
    const val REVEAL_MS = 1500L

    fun draw(name: String, x: Float, y: Float, progress: Float, color: Color): Float {
        val glyphs = name.graphemes()
        val ramp = GradientColors.perChar(glyphs.size, FLAG_STOPS)
        var cx = x
        for ((i, glyph) in glyphs.withIndex()) {
            cx += glyph(glyph, cx, y, lerp(color, Color(ramp[i]), progress))
        }
        val width = cx - x

        for (i in 0 until FLAG_CHIPS) {
            val spin = hash01(i)
            val angle = (-Math.PI / 2 + (spin - 0.5f) * FLAG_SPREAD).toFloat()
            val reach = FLAG_REACH * (0.6f + spin * 0.8f)
            // Arc: progress against progress squared.
            val px = x + width * hash01(i + 97) + cos(angle) * reach * progress
            val py = y + sin(angle) * reach * progress + FLAG_GRAVITY * progress * progress
            val fade = ((1f - progress) / BURST_FADE_AT).coerceIn(0f, 1f)
            if (fade > 0f) {
                flagChip(px, py, FLAG_CHIP_W, (255 * fade).toInt())
            }
        }

        // A few keep coming afterwards, each on its own time-driven cycle.
        for (i in 0 until DRIFT_FLAGS) {
            val phase = hash01(i + 431)
            val period = DRIFT_PERIOD * (0.7f + phase * 0.9f)
            val life = ((System.currentTimeMillis() / 1000.0 * (1f / period) + phase) % 1.0).toFloat()
            // Evenly fanned by index, jittered by hash: six hashed values alone skewed right.
            val slot = (i + 0.5f) / DRIFT_FLAGS - 0.5f
            val angle = (-Math.PI / 2 + slot * DRIFT_SPREAD + (hash01(i + 733) - 0.5f) * DRIFT_JITTER).toFloat()
            val px = x + width * hash01(i + 211) + cos(angle) * DRIFT_REACH * life
            val py = y + sin(angle) * DRIFT_REACH * life + DRIFT_GRAVITY * life * life
            // Fade in and out. A flag popping in mid-air gives it away.
            val fade = sin(life * Math.PI).toFloat() * progress
            if (fade.isVisible) {
                flagChip(px, py, FLAG_CHIP_W * DRIFT_SCALE, (255 * fade).toInt())
            }
        }
        return width
    }

    private fun flagChip(x: Float, y: Float, w: Float, alpha: Int) {
        val h = w * FLAG_CHIP_RATIO
        val band = h / FLAG.size
        for ((i, stripe) in FLAG.withIndex()) {
            Render.rect(x, y + i * band, w, band + 0.5f, stripe.withAlpha(alpha), 0f)
        }
    }

    /** Mirrored about the last color, which is how this flag is built. */
    private fun mirrored(vararg rgb: Int): List<Color> {
        val colors = rgb.map { Color(it) }
        return colors + colors.dropLast(1).reversed()
    }

    private val FLAG = mirrored(0xD260A5, 0xE4AFCD, 0xFEFEFE, 0x57CEF8)

    /** Derived from [FLAG] so the two can't disagree. */
    private val FLAG_STOPS = FLAG.mapIndexed { i, band ->
        ColorStop(band.rgb and 0xFFFFFF, i / (FLAG.size - 1f))
    }

    private const val FLAG_CHIPS = 14
    private const val FLAG_CHIP_W = 14f
    private const val FLAG_CHIP_RATIO = 0.62f
    private const val FLAG_SPREAD = 2.4
    private const val FLAG_REACH = 44f
    private const val FLAG_GRAVITY = 34f

    /** Tail fraction of the reveal spent fading, so chips fall longer. */
    private const val BURST_FADE_AT = 0.35f

    /** Sparse, slow, scattered wider than the burst. */
    private const val DRIFT_FLAGS = 4
    private const val DRIFT_PERIOD = 4.5f
    private const val DRIFT_SPREAD = 4.2
    private const val DRIFT_JITTER = 0.5
    private const val DRIFT_REACH = 30f
    private const val DRIFT_GRAVITY = 66f
    private const val DRIFT_SCALE = 0.8f
}
