package org.blackaddons.core.emoji

import org.blackaddons.api.ui.render.RichSpan
import org.blackaddons.api.ui.widget.text.TextInput
import org.blackaddons.core.emoji.EmojiText.spans

internal object EmojiText {

    private const val MAX_CACHED = 512

    private val cache = object : LinkedHashMap<Pair<String, Int>, List<RichSpan>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, Int>, List<RichSpan>>): Boolean =
            size > MAX_CACHED
    }

    /** Part of any cache key on [spans]. */
    val version: Int get() = CustomEmojiStore.version

    fun spans(text: String): List<RichSpan> = synchronized(cache) {
        cache.getOrPut(text to version) { build(text) }
    }

    /** Null draws the cell as itself. */
    fun cell(cell: String): RichSpan? =
        spans(cell).singleOrNull()?.takeUnless { it is RichSpan.Text && it.text == cell }

    private fun build(text: String): List<RichSpan> = Emoji.segments(text).map { segment ->
        when (segment) {
            is EmojiSegment.Text -> RichSpan.Text(segment.text)
            is EmojiSegment.Glyph -> {
                val (sheet, x, y, size) = Emoji.cellOf(segment.glyph)
                RichSpan.Picture(Emoji.sheetPath(sheet), x.toFloat(), y.toFloat(), size.toFloat(), size.toFloat())
            }
            is EmojiSegment.Custom -> CustomEmojiStore.source(segment.id)?.let { RichSpan.Picture(it, scale = 1.4f) }
                ?: RichSpan.Text(":${segment.name}:")
        }
    }
}

internal fun TextInput.withEmoji(): TextInput = apply {
    editor.cells = Emoji::cells
    editor.appearance = EmojiText::cell
}
