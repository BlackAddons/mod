package org.blackaddons.api.ui.render

import org.blackaddons.api.Platform
import org.blackaddons.api.cosmetics.ColorSpec
import org.blackaddons.api.cosmetics.Cosmetic
import org.blackaddons.api.cosmetics.NickFont
import org.blackaddons.api.cosmetics.NickStyle
import org.blackaddons.api.util.color.GradientColors
import org.blackaddons.api.util.graphemes
import org.blackaddons.api.util.stripFormatting
import java.awt.Color
import kotlin.math.max
import kotlin.random.Random

/**
 * Draws a cosmetic-styled name in our UI with the same rules `PlayerCustomization` uses in-world. A null or
 * name-neutral cosmetic draws [fallback] plainly.
 */
object CosmeticName {

    /** Its `display`, else the real name with `§` codes stripped so it can be recolored. */
    fun textOf(cosmetic: Cosmetic?, fallback: String): String =
        cosmetic?.display?.takeIf { it.isNotEmpty() } ?: fallback.stripFormatting()

    fun width(cosmetic: Cosmetic?, fallback: String, size: Float, font: Font): Float {
        val text = textOf(cosmetic, fallback)
        val c = cosmetic?.takeIf { (it.affectsName || it.hasPrefix) && !text.contains('§') }
        val name = when {
            c?.affectsName != true -> Render.textWidth(text, size, font)
            c.font == null -> Render.textWidth(text, size, font.with(c.style))
            else -> text.graphemes().sumOf { Render.textWidth(it, size, letterFont(it, c.font, font).with(c.style)).toDouble() }.toFloat()
        }
        val prefix = c?.takeIf { it.hasPrefix }?.prefix ?: return name
        return Render.textWidth(prefix, size, font.with(c.prefixStyle)) + Render.textWidth(" ", size, font) + name
    }

    /**
     * The cosmetic with its name cut to fit [maxWidth] as [draw] paints it: prefix, nick font and style included.
     * Draw the result with the same [fallback].
     */
    fun ellipsize(cosmetic: Cosmetic?, fallback: String, maxWidth: Number, size: Float, font: Font): Cosmetic? {
        val text = textOf(cosmetic, fallback)
        val fitted = Render.ellipsize(text, maxWidth) { width(cosmetic.withDisplay(it), fallback, size, font) }
        return if (fitted == text) cosmetic else cosmetic.withDisplay(fitted)
    }

    private fun Cosmetic?.withDisplay(display: String): Cosmetic =
        this?.copy(display = display) ?: Cosmetic(display = display)

    /** [plain] colors an unstyled name. */
    fun draw(
        cosmetic: Cosmetic?, fallback: String,
        x: Float, y: Float, size: Float, font: Font, plain: Color,
    ): Float {
        val text = textOf(cosmetic, fallback)
        val c = cosmetic?.takeIf { it.affectsName || it.hasPrefix }
        // Pre-formatted (`§`) names pass through verbatim, as in-world.
        if (c == null || text.contains('§')) return solid(text, x, y, size, font, plain)

        var cx = x
        if (c.hasPrefix) {
            cx += drawSpec(c.prefix!!, c.prefixColor, c.prefixStyle, null, cx, y, size, font, plain)
            cx += solid(" ", cx, y, size, font, plain)
        }
        cx += if (c.affectsName) drawSpec(text, c.name, c.style, c.font, cx, y, size, font, plain)
        else solid(text, cx, y, size, font, plain)
        return cx - x
    }

    /** Returns the drawn width. Mirrors PlayerCustomization's `styled`. */
    private fun drawSpec(
        text: String, spec: ColorSpec, style: NickStyle, nick: NickFont?,
        x: Float, y: Float, size: Float, font: Font, plain: Color,
    ): Float {
        val styled = font.with(style)
        val fixed = when {
            spec.chroma || spec.gradient.isNotEmpty() -> null
            spec.solidColor != null -> Color(spec.solidColor)
            else -> plain
        }
        val lined = style.underlined || style.strikethrough
        if (fixed != null && !lined && !style.obfuscated && nick == null) return solid(text, x, y, size, styled, fixed)

        // Per letter, the way vanilla draws text: the letter's color, its obfuscated stand-in and its line segment.
        val glyphs = text.graphemes()
        val n = glyphs.size
        val time = System.currentTimeMillis() / 1000.0
        val metrics = if (lined) Render.textMetrics(size, styled) else null
        var cx = x
        for (i in glyphs.indices) {
            val color = fixed ?: Color((0xFF shl 24) or (letterRgb(spec, i, n, time) and 0xFFFFFF), true)
            val letter = if (nick == null) styled else letterFont(glyphs[i], nick, font).with(style)
            Render.text(if (style.obfuscated) scrambled(i, time) else glyphs[i], cx, y, size, color, letter)
            // Uses the real letter's advance, so the obfuscated letters do not make the line jitter.
            val w = Render.textWidth(glyphs[i], size, letter)
            if (metrics != null) lines(style, cx, w, y, size, metrics, color)
            cx += w
        }
        return cx - x
    }

    private fun letterRgb(spec: ColorSpec, i: Int, n: Int, time: Double): Int {
        /*
         * The phase is reversed so a wave flows from left to right. `solid` gradients pulse in unison, as in
         * Render.gradientText.
         */
        val wave = if (n > 1) (n - 1 - i).toFloat() / (n - 1) else 0f
        if (spec.chroma) return GradientColors.chromaColor(spec.speed, wave * GradientColors.CHROMA_HUE_SPREAD, time)
        val unison = !spec.wholeTextGradient || n <= 1
        return if (spec.animated) GradientColors.animatedGradientColor(spec.gradient, spec.speed, if (unison) 0f else wave, time)
        else GradientColors.colorAt(spec.gradient, if (unison) 0f else i.toFloat() / (n - 1))
    }

    // Close to vanilla's thickness and placement: below the baseline, and through the middle of the lowercase letters.
    private fun lines(style: NickStyle, x: Float, w: Float, y: Float, size: Float, metrics: FloatArray, color: Color) {
        val thickness = max(1f, size / LINE_DIVISOR)
        val baseline = y + metrics[0]
        if (style.underlined) {
            val ly = baseline + metrics[1] * UNDERLINE_DROP
            Render.line(x, ly, x + w, ly, thickness, color)
        }
        if (style.strikethrough) {
            val ly = baseline - metrics[0] * STRIKE_RISE
            Render.line(x, ly, x + w, ly, thickness, color)
        }
    }

    // A new random letter per slot every tick, like vanilla's obfuscated text.
    private fun scrambled(index: Int, time: Double): String {
        val tick = (time * TICKS_PER_SECOND).toLong()
        return SCRAMBLE[Random(tick * SCRAMBLE.length + index).nextInt(SCRAMBLE.length)].toString()
    }

    /**
     * The file whose range covers the letter, once it is on disk. The base font until then, and for letters that no
     * file covers.
     */
    private fun letterFont(letter: String, nick: NickFont, base: Font): Font {
        val codePoint = letter.codePointAt(0)
        val file = nick.files.firstOrNull { it.covers(codePoint) } ?: return base
        return Platform.cosmetics.fontFile(file)?.let { Font(Render.FILE_SCHEME + it) } ?: base
    }

    private fun Font.with(style: NickStyle): Font =
        if (!style.bold && !style.italic) this else copy(bold = bold || style.bold, italic = italic || style.italic)

    private fun solid(text: String, x: Float, y: Float, size: Float, font: Font, color: Color): Float {
        Render.text(text, x, y, size, color, font)
        return Render.textWidth(text, size, font)
    }

    private const val LINE_DIVISOR = 12f
    private const val UNDERLINE_DROP = 0.35f
    private const val STRIKE_RISE = 0.3f
    private const val TICKS_PER_SECOND = 20
    private const val SCRAMBLE = "ABCDEFGHJKLMNOPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz0123456789"
}
