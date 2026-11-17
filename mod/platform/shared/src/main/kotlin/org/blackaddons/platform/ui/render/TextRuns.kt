package org.blackaddons.platform.ui.render

import io.github.humbleui.skija.Typeface
import java.util.BitSet

/** A stretch of one line a single source draws: the font itself, the fallback font, or one Hypixel icon. */
internal sealed class TextRun(val text: String) {
    class Main(text: String) : TextRun(text)
    class Fallback(text: String) : TextRun(text)
    class Icon(text: String, val glyph: HypixelGlyphs.Glyph) : TextRun(text)
}

/**
 * Splits a line where the font lacks a character, so it's drawn by [fallback] (unifont, same as Minecraft falls back)
 * or as a Hypixel icon instead of a box. Null means the font covers the whole line, the common case, which then draws
 * exactly like before. Render thread only.
 */
internal class TextRuns(private val fallback: Typeface?) {

    private val coverage = HashMap<Typeface, Coverage>()

    fun split(text: String, typeface: Typeface?): List<TextRun>? {
        if (typeface == null) return null
        val main = coverage.getOrPut(typeface) { Coverage(typeface) }
        val backup = fallback?.let { coverage.getOrPut(it) { Coverage(it) } }
        if (text.all { main.covers(it) }) return null

        val runs = ArrayList<TextRun>()
        val pending = StringBuilder()
        var pendingFallback = false
        fun flush() {
            if (pending.isEmpty()) return
            runs += if (pendingFallback) TextRun.Fallback(pending.toString()) else TextRun.Main(pending.toString())
            pending.clear()
        }
        for (ch in text) {
            val icon = HypixelGlyphs.of(ch.code)
            if (icon != null) {
                flush()
                runs += TextRun.Icon(ch.toString(), icon)
                continue
            }
            // Surrogates and characters neither face has stay with the font, its own fallback might still find them.
            val useFallback = !main.covers(ch) && backup?.covers(ch) == true && !HypixelGlyphs.isPrivateUse(ch.code)
            if (useFallback != pendingFallback) {
                flush()
                pendingFallback = useFallback
            }
            pending.append(ch)
        }
        flush()
        return runs.takeUnless { it.size == 1 && it[0] is TextRun.Main }
    }

    private class Coverage(private val typeface: Typeface) {
        private val known = BitSet()
        private val covered = BitSet()

        fun covers(ch: Char): Boolean {
            if (ch.isSurrogate() || ch < ' ') return true
            val code = ch.code
            if (!known[code]) {
                known.set(code)
                if (typeface.getUTF32Glyph(code) != NO_GLYPH) covered.set(code)
            }
            return covered[code]
        }
    }

    private companion object {
        const val NO_GLYPH: Short = 0
    }
}
