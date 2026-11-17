package org.blackaddons.api.chat

import org.blackaddons.api.util.color.ColorStop
import org.blackaddons.api.util.color.GradientColors
import java.awt.Color
import java.util.UUID

/** What clicking a [ChatSpan] does. Mirrors Minecraft's click events without `net.minecraft`. */
sealed interface ChatClick {
    /** No leading `/` - the platform adds it. */
    data class RunCommand(val command: String) : ChatClick

    /** Pre-fills the chat box (no leading `/`). */
    data class SuggestCommand(val command: String) : ChatClick

    /** The client may ask for confirmation. */
    data class OpenUrl(val url: String) : ChatClick

    data class CopyToClipboard(val text: String) : ChatClick
}

data class ChatSpan(
    val text: String,
    val color: Color? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underlined: Boolean = false,
    val strikethrough: Boolean = false,
    val obfuscated: Boolean = false,
    val click: ChatClick? = null,
    val hover: String? = null,
    /** Resource font (`namespace:path`) for this run. */
    val font: String? = null,
    /** Draws this player's face (with the hat layer) in place of [text]. Copying and matching still read [text]. */
    val head: UUID? = null,
)

/**
 * Rich chat line made of [ChatSpan]s, without depending on Minecraft's `Component`.
 *
 * ```
 * Platform.chat.local(ChatMessage.build {
 *     append("[BlackAddons] ", Color(108, 167, 255), bold = true)
 *     append("Auth failed. ")
 *     append("[Retry]", Color(124, 224, 162), underlined = true,
 *             click = ChatClick.RunCommand("blackaddons reauth"), hover = "Click to retry")
 * })
 * ```
 */
class ChatMessage(val spans: List<ChatSpan>) {

    class Builder {
        private val spans = ArrayList<ChatSpan>()

        fun append(
            text: String,
            color: Color? = null,
            bold: Boolean = false,
            italic: Boolean = false,
            underlined: Boolean = false,
            strikethrough: Boolean = false,
            obfuscated: Boolean = false,
            click: ChatClick? = null,
            hover: String? = null,
            font: String? = null,
        ): Builder {
            spans += ChatSpan(
                text, color, bold, italic, underlined, strikethrough, obfuscated, click, hover, font,
            )
            return this
        }

        /** The face of [player], one glyph wide, the way the game draws heads in chat. Copies show [text] instead. */
        fun appendHead(player: UUID, text: String = "", hover: String? = null): Builder {
            spans += ChatSpan(text, hover = hover, head = player)
            return this
        }

        /**
         * Left-to-right gradient from [from] to [to], one span per character (chat has no other way). Style
         * and click/hover apply to the whole run.
         */
        fun appendGradient(
            text: String,
            from: Color,
            to: Color,
            bold: Boolean = false,
            italic: Boolean = false,
            underlined: Boolean = false,
            strikethrough: Boolean = false,
            obfuscated: Boolean = false,
            click: ChatClick? = null,
            hover: String? = null,
        ): Builder {
            val stops = listOf(ColorStop(from.rgb and 0xFFFFFF, 0f), ColorStop(to.rgb and 0xFFFFFF, 1f))
            val colors = GradientColors.perChar(text.length, stops)
            text.forEachIndexed { i, char ->
                spans += ChatSpan(
                    char.toString(), Color(colors[i]),
                    bold, italic, underlined, strikethrough, obfuscated, click, hover,
                )
            }
            return this
        }

        fun build(): ChatMessage = ChatMessage(spans.toList())
    }

    companion object {
        inline fun build(block: Builder.() -> Unit): ChatMessage = Builder().apply(block).build()

        fun of(text: String, color: Color? = null): ChatMessage =
            ChatMessage(listOf(ChatSpan(text, color)))

        fun gradient(text: String, from: Color, to: Color): ChatMessage =
            build { appendGradient(text, from, to) }

        /** Legacy `&`/`§` codes, vanilla rules: a color clears style flags, `&r` all, a non-code `&` stays literal. */
        fun legacy(text: String): ChatMessage {
            val spans = ArrayList<ChatSpan>()
            val run = StringBuilder()
            var color: Color? = null
            var bold = false
            var italic = false
            var underlined = false
            var strikethrough = false
            var obfuscated = false

            fun flush() {
                if (run.isEmpty()) return
                spans += ChatSpan(
                    run.toString(), color, bold, italic, underlined, strikethrough, obfuscated,
                )
                run.setLength(0)
            }

            fun clearStyles() {
                bold = false
                italic = false
                underlined = false
                strikethrough = false
                obfuscated = false
            }

            var i = 0
            while (i < text.length) {
                val c = text[i]
                val code = if ((c == '&' || c == '§') && i + 1 < text.length) {
                    text[i + 1].lowercaseChar()
                } else {
                    null
                }
                if (code == null || !(LEGACY_COLORS.containsKey(code) || code in STYLE_CODES)) {
                    run.append(c)
                    i++
                    continue
                }
                flush()
                when (code) {
                    'r' -> { color = null; clearStyles() }
                    'l' -> bold = true
                    'o' -> italic = true
                    'n' -> underlined = true
                    'm' -> strikethrough = true
                    'k' -> obfuscated = true
                    else -> { color = LEGACY_COLORS[code]; clearStyles() }
                }
                i += 2
            }
            flush()
            return ChatMessage(spans)
        }

        private const val STYLE_CODES = "klmnor"

        private val LEGACY_COLORS = mapOf(
            '0' to Color(0x000000), '1' to Color(0x0000AA), '2' to Color(0x00AA00),
            '3' to Color(0x00AAAA), '4' to Color(0xAA0000), '5' to Color(0xAA00AA),
            '6' to Color(0xFFAA00), '7' to Color(0xAAAAAA), '8' to Color(0x555555),
            '9' to Color(0x5555FF), 'a' to Color(0x55FF55), 'b' to Color(0x55FFFF),
            'c' to Color(0xFF5555), 'd' to Color(0xFF55FF), 'e' to Color(0xFFFF55),
            'f' to Color(0xFFFFFF),
        )
    }
}
