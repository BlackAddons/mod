package org.blackaddons.core.emoji

import org.blackaddons.api.chat.ChatMessage
import org.blackaddons.api.ui.theme.Theme
import java.awt.Color

/**
 * Twemoji as its emoji-font char, white so chat tint doesn't change it. Custom emoji as `:name:`, vanilla can't draw
 * them.
 */
fun ChatMessage.Builder.appendWithEmoji(text: String, color: Color, hover: String? = null) {
    for (segment in Emoji.segments(text)) when (segment) {
        is EmojiSegment.Text -> append(segment.text, color, hover = hover)
        is EmojiSegment.Glyph -> append(Emoji.pua(segment.glyph).toString(), Color.WHITE, hover = hover, font = Emoji.FONT)
        is EmojiSegment.Custom -> append(":${segment.name}:", Theme.ACCENT, hover = hover ?: ":${segment.name}:")
    }
}

fun plainEmoji(text: String): String =
    Emoji.segments(text).joinToString("") { segment ->
        when (segment) {
            is EmojiSegment.Text -> segment.text
            is EmojiSegment.Glyph -> segment.text
            is EmojiSegment.Custom -> ":${segment.name}:"
        }
    }
