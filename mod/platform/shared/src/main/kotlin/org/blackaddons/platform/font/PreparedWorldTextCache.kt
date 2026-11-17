package org.blackaddons.platform.font

import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence
import net.minecraft.util.FormattedCharSink
import org.blackaddons.platform.font.PreparedWorldTextCache.Entry

/*
 * Nametags, holograms and signs repeat every frame. Cleared on font reload, because cached glyphs point into freed
 * atlases.
 */
object PreparedWorldTextCache {

    private const val MAX_ENTRIES = 1024

    private class Entry(val text: Font.PreparedText, val worldGlyphs: Int)

    private val entries = object : LinkedHashMap<Key, Entry>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Entry>): Boolean = size > MAX_ENTRIES
    }

    private val probe = Key()

    @JvmStatic
    fun invalidate() {
        entries.clear()
    }

    @JvmStatic
    fun prepare(
        text: FormattedCharSequence, x: Float, y: Float, color: Int, shadow: Boolean, background: Int,
        original: () -> Font.PreparedText,
    ): Font.PreparedText {
        if (!probe.read(text)) return original()
        probe.seal(x, y, color, shadow, background, contextKey(), GlobalFontRenderContext.worldGlyphs)
        entries[probe]?.let {
            GlobalFontRenderContext.skipWorldGlyphs(it.worldGlyphs)
            return it.text
        }
        val start = GlobalFontRenderContext.worldGlyphs
        GlobalFontRenderContext.provisional = false
        val prepared = original()
        if (!GlobalFontRenderContext.provisional) {
            entries[probe.copy()] = Entry(prepared, GlobalFontRenderContext.worldGlyphs - start)
        }
        return prepared
    }

    private fun contextKey(): Long {
        if (!MinecraftGlobalFonts.isEnabled()) return 0L
        val bucket = MinecraftGlobalFonts.worldBucketKey(GlobalFontRenderContext.worldPixelsPerUnit)
        val suppressed = if (GlobalFontEffects.suppressed) 1L else 0L
        return 1L or (suppressed shl 1) or (GlobalFontRenderContext.current.toLong() shl 2) or (bucket.toLong() shl 8)
    }

    private class Key(
        private var codepoints: IntArray = IntArray(64),
        private var styles: Array<Style?> = arrayOfNulls(64),
        private var size: Int = 0,
    ) : FormattedCharSink {
        private var obfuscated = false
        private var x = 0f
        private var y = 0f
        private var color = 0
        private var shadow = false
        private var background = 0
        private var context = 0L
        private var worldGlyphStart = 0
        private var hash = 0

        fun read(text: FormattedCharSequence): Boolean {
            size = 0
            obfuscated = false
            text.accept(this)
            return !obfuscated
        }

        override fun accept(position: Int, style: Style, codepoint: Int): Boolean {
            if (style.isObfuscated) {
                obfuscated = true
                return false
            }
            if (size == codepoints.size) {
                codepoints = codepoints.copyOf(size * 2)
                styles = styles.copyOf(size * 2)
            }
            codepoints[size] = codepoint
            styles[size] = style
            size++
            return true
        }

        fun seal(x: Float, y: Float, color: Int, shadow: Boolean, background: Int, context: Long, worldGlyphStart: Int) {
            this.x = x
            this.y = y
            this.color = color
            this.shadow = shadow
            this.background = background
            this.context = context
            this.worldGlyphStart = worldGlyphStart
            var h = size
            var previous: Style? = null
            for (i in 0 until size) {
                h = h * 31 + codepoints[i]
                val style = styles[i]
                if (style !== previous) {
                    h = h * 31 + System.identityHashCode(style)
                    previous = style
                }
            }
            h = h * 31 + x.toRawBits()
            h = h * 31 + y.toRawBits()
            h = h * 31 + color
            h = h * 31 + background
            h = h * 31 + context.hashCode()
            h = h * 31 + worldGlyphStart
            hash = if (shadow) h.inv() else h
        }

        fun copy(): Key = Key(codepoints.copyOf(size), styles.copyOf(size), size).also {
            it.x = x
            it.y = y
            it.color = color
            it.shadow = shadow
            it.background = background
            it.context = context
            it.worldGlyphStart = worldGlyphStart
            it.hash = hash
        }

        override fun hashCode(): Int = hash

        override fun equals(other: Any?): Boolean {
            if (other !is Key || other.hash != hash || other.size != size) return false
            if (other.x != x || other.y != y || other.color != color || other.shadow != shadow) return false
            if (other.background != background || other.context != context || other.worldGlyphStart != worldGlyphStart) return false
            for (i in 0 until size) {
                if (other.codepoints[i] != codepoints[i] || other.styles[i] !== styles[i]) return false
            }
            return true
        }
    }
}
