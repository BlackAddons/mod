package org.blackaddons.core.ui.tab.eggs

import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import java.awt.Color

/**
 * The Matrix, with the row itself becoming the line. Stateless: positions and glyphs derive from the clock and column
 * index, like `Chroma`.
 */
internal object AutismoEgg {

    /** Scales with progress so the list opens instead of jumping. */
    fun extraHeight(progress: Float): Float = quoteHeight() * progress

    /**
     * The row becomes the film's line with the nick in it: the prefix materializes, the name slides right and
     * cross-fades into the terminal font.
     */
    fun draw(name: String, x: Float, y: Float, progress: Float, color: Color): Float {
        val alpha = (255 * progress).toInt().coerceIn(0, 255)
        val prefixW = Render.textWidth(QUOTE_OPEN, Theme.FONT_BODY, TERMINAL)
        val struckW = Render.textWidth(QUOTE_STRUCK, Theme.FONT_BODY, TERMINAL)
        val spaceW = Render.textWidth(" ", Theme.FONT_BODY, TERMINAL)
        val height = Render.textHeight(Theme.FONT_BODY, TERMINAL)

        Render.text(QUOTE_OPEN, x, y, Theme.FONT_BODY, QUOTE_DIM.withAlpha(alpha), TERMINAL)
        Render.text(QUOTE_STRUCK, x + prefixW, y, Theme.FONT_BODY, QUOTE_FADED.withAlpha(alpha), TERMINAL)
        Render.rect(x + prefixW, y + height / 2f, struckW, 1f, QUOTE_FADED.withAlpha(alpha), 0f)

        val nameX = x + (prefixW + struckW + spaceW) * progress
        val plainW = Render.textWidth(name, Theme.FONT_BODY, Fonts.medium)
        val termW = Render.textWidth(name, Theme.FONT_BODY, TERMINAL)
        Render.text(name, nameX, y, Theme.FONT_BODY, lerp(color, NAME_GREEN, progress).withAlpha(255 - alpha), Fonts.medium)
        Render.text(name, nameX, y, Theme.FONT_BODY, NAME_GREEN.withAlpha(alpha), TERMINAL)

        val nameW = plainW + (termW - plainW) * progress
        Render.text(QUOTE_TRAIL, nameX + nameW, y, Theme.FONT_BODY, QUOTE_DIM.withAlpha(alpha), TERMINAL)
        return nameX - x + nameW + Render.textWidth(QUOTE_TRAIL, Theme.FONT_BODY, TERMINAL) * progress
    }

    /** Second line, blinking cursor and the rain below. */
    fun drawBelow(x: Float, y: Float, progress: Float) {
        val alpha = (255 * progress).toInt().coerceIn(0, 255)
        val height = Render.textHeight(Theme.FONT_BODY, TERMINAL)
        Render.text(QUOTE_SECOND, x, y, Theme.FONT_BODY, QUOTE_DIM.withAlpha(alpha), TERMINAL)

        // A terminal that has said its piece just sits there blinking.
        val lineW = Render.textWidth(QUOTE_SECOND, Theme.FONT_BODY, TERMINAL)
        if ((System.currentTimeMillis() / CURSOR_BLINK_MS) % 2L == 0L) {
            Render.rect(x + lineW + 2f, y + height * 0.15f, CURSOR_W, height * 0.75f, NAME_GREEN.withAlpha(alpha), 0f)
        }

        rain(x, y + height + QUOTE_LINE_GAP, lineW, progress)
    }

    private fun rain(x: Float, y: Float, width: Float, progress: Float) {
        val cell = Render.textWidth(GLYPHS.take(1), Theme.FONT_BODY, RAIN_FONT).coerceAtLeast(1f)
        val step = cell * COLUMN_SPACING
        val seconds = System.currentTimeMillis() / 1000.0
        val columns = (width / step).toInt().coerceAtLeast(1)
        val tailPx = RAIN_TAIL * cell

        for (i in 0 until columns) {
            // Per-column phase and speed from a hash, so columns don't fall in step.
            val phase = hash01(i)
            val speed = RAIN_MIN_SPEED + phase * RAIN_SPEED_SPREAD
            // Each cycle is longer than the strip, leaving dead air between streams and no visible restart.
            val period = RAIN_HEIGHT * (RAIN_CYCLE_MIN + phase * RAIN_CYCLE_SPREAD) + tailPx
            val head = ((seconds * speed + phase * period) % period).toFloat() - tailPx

            for (row in 0 until RAIN_TAIL) {
                val gy = y + head - row * cell
                if (gy < y || gy > y + RAIN_HEIGHT) continue
                // Brightest at the head, fades up the tail and toward the bottom.
                val tail = 1f - row.toFloat() / RAIN_TAIL
                val depth = 1f - (gy - y) / RAIN_HEIGHT
                val alpha = (255 * tail * depth * progress).toInt().coerceIn(0, 255)
                if (alpha <= 0) continue
                val shade = if (row == 0) RAIN_HEAD else RAIN_BODY
                Render.text(glyphAt(i, row, seconds), x + i * step, gy, Theme.FONT_BODY, shade.withAlpha(alpha), RAIN_FONT)
            }
        }
    }

    /** Only the second line reserves space, the rain can fall past the next row. */
    private fun quoteHeight(): Float =
        Render.textHeight(Theme.FONT_BODY, TERMINAL) + QUOTE_LINE_GAP + QUOTE_PAD

    private fun glyphAt(column: Int, row: Int, seconds: Double): String {
        val step = (seconds / GLYPH_SWAP_SECONDS).toInt()
        val index = (column * 31 + row * 17 + step * 7) % GLYPHS.length
        return GLYPHS[if (index < 0) index + GLYPHS.length else index].toString()
    }

    /** Plain katakana (the films' glyphs are a designed asset). Tofu without a CJK font: check in game. */
    private const val GLYPHS = "アイウエオカキクケコサシスセソタチツテトナニヌネノ0123456789"

    private const val GLYPH_SWAP_SECONDS = 0.09
    private const val RAIN_HEIGHT = 46f
    private const val RAIN_TAIL = 6
    private const val RAIN_MIN_SPEED = 14f
    private const val RAIN_SPEED_SPREAD = 26f

    /** At 1.0 the rain is a solid sheet. */
    private const val COLUMN_SPACING = 1.15f

    /** Where the dead air between streams comes from. */
    private const val RAIN_CYCLE_MIN = 1.05f
    private const val RAIN_CYCLE_SPREAD = 0.8f

    private val RAIN_HEAD = Color(0xC8FFD4)
    private val RAIN_BODY = Color(0x2FBF4F)
    private val NAME_GREEN = Color(0x2FD45C)

    /** The typewriter face the film's own titles are set in. */
    private val TERMINAL = Fonts.courier

    /** Courier Prime has no katakana. */
    private val RAIN_FONT = Fonts.unifont

    private const val CURSOR_BLINK_MS = 500L
    private const val CURSOR_W = 6f

    private const val QUOTE_OPEN = "Wake up, "
    private const val QUOTE_STRUCK = "Neo"
    private const val QUOTE_TRAIL = "..."
    private const val QUOTE_SECOND = "The Matrix has you..."
    private const val QUOTE_LINE_GAP = 3f
    private const val QUOTE_PAD = 6f

    private val QUOTE_DIM = Color(0x2FBF4F)
    private val QUOTE_FADED = Color(0x1E7A34)
}
