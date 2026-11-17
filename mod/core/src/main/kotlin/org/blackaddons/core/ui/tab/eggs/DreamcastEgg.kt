package org.blackaddons.core.ui.tab.eggs

import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.util.graphemes
import java.awt.Color
import kotlin.math.abs
import kotlin.math.sin

/** Doctor Who: the name becomes the war cry, and a small blue box lands beside it, then flies around the tab. */
internal object DreamcastEgg {

    /** The box takes its time, because landing is the whole point. */
    const val REVEAL_MS = 2300L

    /**
     * The name is eaten from the left while the war cry types in over the space it frees - one letter
     * overwriting another, which is a better joke than a fade and reads as something *taking* the row.
     *
     * Set in syllables because that is how the things speak and how the subtitles have always written
     * it. A word is not a character: single words and short phrases carry no copyright, and this one is
     * an ordinary English verb - which is exactly why it is safe where a drawing of the creature would
     * not be.
     *
     * The letters never sit still: a slow per-character wobble, keyed off the clock and the index,
     * stands in for the ring-modulated voice.
     */
    fun draw(name: String, x: Float, y: Float, progress: Float, color: Color): Float {
        val seconds = System.currentTimeMillis() / 1000.0
        val erased = (progress / ERASE_AT).coerceIn(0f, 1f)
        val typed = ((progress - TYPE_FROM) / (1f - TYPE_FROM)).coerceIn(0f, 1f)

        var cx = x
        val nameGraphemes = name.graphemes()
        val nameCount = nameGraphemes.size.coerceAtLeast(1)
        for ((i, glyph) in nameGraphemes.withIndex()) {
            val w = Render.textWidth(glyph, Theme.FONT_BODY, Fonts.medium)
            // Gone once the wipe passes. The last letter fades instead of blinking.
            val alive = (i + 1f) / nameCount - erased
            if (alive > 0f) {
                val fade = (alive * nameCount).coerceIn(0f, 1f)
                Render.text(glyph, cx, y, Theme.FONT_BODY, color.withAlpha((255 * fade).toInt()), Fonts.medium)
            }
            cx += w
        }

        var tx = x
        val shown = CRY.length * typed
        for ((i, char) in CRY.withIndex()) {
            val glyph = char.toString()
            val w = Render.textWidth(glyph, Theme.FONT_BODY, Fonts.bold)
            val landed = (shown - i).coerceIn(0f, 1f)
            if (landed > 0f) {
                // Freshly landed letters flash, then settle into the casing's gold.
                val shade = lerp(CRY_FLASH, CRY_GOLD, landed)
                val wobble = sin(seconds * CRY_WOBBLE_RATE + i).toFloat() * CRY_WOBBLE_PX * landed
                Render.text(glyph, tx, y + wobble, Theme.FONT_BODY, shade.withAlpha((255 * landed).toInt()), Fonts.bold)
            }
            tx += w
        }

        val nameW = Render.textWidth(name, Theme.FONT_BODY, Fonts.medium)
        val cryW = Render.textWidth(CRY, Theme.FONT_BODY, Fonts.bold)
        return nameW + (cryW - nameW) * progress
    }

    /** Lands by the name, wanders in [w]x[h]. Pure in [progress] and clock: closing flies it home to dematerialize. */
    fun drawFlight(originX: Float, originY: Float, x: Float, y: Float, w: Float, h: Float, progress: Float) {
        if (progress <= 0f) return

        val seconds = System.currentTimeMillis() / 1000.0
        val margin = BOX_H
        val cx = x + w / 2f
        val cy = y + h / 2f
        // Beside the name, not on it - the show puts it next to whoever it came for.
        val homeX = originX - BOX_H * BOX_ASPECT / 2f - BOX_GAP
        // Two slow sines of unrelated periods: no visible loop, no stored coordinates.
        val pathX = cx + (w / 2f - margin) * sin(seconds * FLIGHT_X_RATE).toFloat()
        val pathY = cy + (h / 2f - margin) * sin(seconds * FLIGHT_Y_RATE + FLIGHT_Y_PHASE).toFloat()

        // Fully lands before takeoff, closing runs the same order back.
        val takeoff = ((progress - LANDED_AT) / (1f - LANDED_AT)).coerceIn(0f, 1f)
        val eased = takeoff * takeoff * (3f - 2f * takeoff)
        val boxX = homeX + (pathX - homeX) * eased
        val boxY = originY + (pathY - originY) * eased

        // Rocks instead of tumbling, tumbling reads as debris.
        val tilt = sin(seconds * FLIGHT_ROCK_RATE).toFloat() * FLIGHT_ROCK_DEGREES * eased
        Render.push()
        try {
            Render.translate(boxX, boxY)
            Render.rotate(tilt)
            Render.translate(-boxX, -boxY)
            blueBox(boxX, boxY, progress)
        } finally {
            Render.pop()
        }
    }

    /**
     * Materializes with a stutter and a blinking lamp. Deliberately not that box: no sign, no paneling. The design
     * belongs to the BBC, who won it in court against an actual police force.
     */
    private fun blueBox(x: Float, y: Float, progress: Float) {
        // Solidity stutters up while landing, then stays solid.
        val landing = (progress / LANDED_AT).coerceIn(0f, 1f)
        val stutter = if (landing >= 1f) 1f else 0.25f + 0.75f * abs(sin(landing * ARRIVAL_WOBBLE))
        val alpha = (255 * landing * stutter).toInt().coerceIn(0, 255)

        val scale = 0.35f + 0.65f * landing
        val h = BOX_H * scale
        val w = h * BOX_ASPECT
        // Centered on the given point, so the path can steer it.
        val bx = x - w / 2f
        val by = y - h / 2f

        Render.rect(bx - w * 0.08f, by, w * 1.16f, h * 0.09f, BOX_ROOF.withAlpha(alpha), 1f)
        Render.rect(bx, by + h * 0.09f, w, h * 0.91f, BOX_BLUE.withAlpha(alpha), 1f)
        val paneY = by + h * 0.18f
        val paneH = h * 0.20f
        Render.rect(bx + w * 0.12f, paneY, w * 0.30f, paneH, BOX_PANE.withAlpha(alpha), 0f)
        Render.rect(bx + w * 0.58f, paneY, w * 0.30f, paneH, BOX_PANE.withAlpha(alpha), 0f)
        Render.rect(bx + w * 0.49f, by + h * 0.09f, 1f, h * 0.91f, BOX_SEAM.withAlpha(alpha), 0f)

        // The lamp on top keeps time even once it has landed.
        val lit = (System.currentTimeMillis() / LAMP_BLINK_MS) % 2L == 0L
        val lamp = if (lit) BOX_LAMP else BOX_ROOF
        Render.rect(bx + w * 0.40f, by - h * 0.07f, w * 0.20f, h * 0.08f, lamp.withAlpha(alpha), 1f)
    }

    /** The cry, in syllables, like the subtitles have always set it. */
    private const val CRY = "EX-TER-MIN-ATE!"

    /** The typing starts just before the wipe ends. */
    private const val ERASE_AT = 0.55f
    private const val TYPE_FROM = 0.35f

    private const val CRY_WOBBLE_RATE = 7.0
    private const val CRY_WOBBLE_PX = 0.7f

    private val CRY_GOLD = Color(0xD9A441)
    private val CRY_FLASH = Color(0xFFF6D8)

    /** Tall and narrow, in a blue that's a blue and not a trademark. */
    private const val BOX_H = 30f
    private const val BOX_ASPECT = 0.62f
    private const val ARRIVAL_WOBBLE = 26f
    private const val BOX_GAP = 10f

    /** How much of the reveal is spent landing before the box goes anywhere. */
    private const val LANDED_AT = 0.55f

    /** Two unrelated rates, so the wander never lines up into a visible loop. */
    private const val FLIGHT_X_RATE = 0.23
    private const val FLIGHT_Y_RATE = 0.17
    private const val FLIGHT_Y_PHASE = 1.1
    private const val FLIGHT_ROCK_RATE = 0.9
    private const val FLIGHT_ROCK_DEGREES = 9f
    private const val LAMP_BLINK_MS = 700L

    private val BOX_BLUE = Color(0x1B3F72)
    private val BOX_ROOF = Color(0x152F55)
    private val BOX_PANE = Color(0x9FC4E8)
    private val BOX_SEAM = Color(0x102640)
    private val BOX_LAMP = Color(0xFFF3C4)
}
