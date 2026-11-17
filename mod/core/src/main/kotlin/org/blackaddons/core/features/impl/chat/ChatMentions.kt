package org.blackaddons.core.features.impl.chat

import org.blackaddons.api.Platform
import org.blackaddons.api.chat.PlayerMessage
import org.blackaddons.api.event.chat.ChatLineAddedEvent
import org.blackaddons.api.feature.Feature
import java.awt.Color

object ChatMentions : Feature(
    description = "Highlights chat messages that mention your name or a keyword, with an optional sound.",
    name = "Chat Mentions",
) {

    private val yourName by boolean("Your Name", true)
        .withDescription("Messages that mention your username count as mentions")
    private val keywords by list("Keywords", placeholder = "Keyword")
        .withDescription("Extra words or phrases that count as mentions")
    private val highlightColor by color("Highlight Color", Color(255, 170, 0, 70))
        .withDescription("Tint drawn behind a message that mentions you")
    private val playSound by boolean("Play Sound", true)
        .withDescription("Ping when a mention arrives")

    private var terms: List<String> = emptyList()
    private var pattern: Regex? = null

    override fun init() {
        register<ChatLineAddedEvent> {
            val me = Platform.player.name
            if (!mentions(event.line.unformatted, me)) return@register
            event.line.highlight = highlightColor.value
            if (playSound.value) Platform.feedback.playSound(SOUND, pitch = SOUND_PITCH)
        }
    }

    internal fun mentions(text: String, me: String?): Boolean {
        if (pattern(me)?.containsMatchIn(text) != true) return false
        if (text.startsWith(OUTGOING_DM)) return false
        return me == null || !PlayerMessage.of(text)?.player.equals(me, ignoreCase = true)
    }

    private fun pattern(me: String?): Regex? {
        val current = buildList {
            if (yourName.value && me != null) add(me)
            keywords.value.mapNotNullTo(this) { it.trim().ifBlank { null } }
        }
        if (current != terms) {
            terms = current
            pattern = current.takeIf { it.isNotEmpty() }?.let { words ->
                Regex(words.joinToString("|", "(?<!\\w)(?:", ")(?!\\w)") { Regex.escape(it) }, RegexOption.IGNORE_CASE)
            }
        }
        return pattern
    }

    private const val OUTGOING_DM = "To "
    // TODO: add customization for sound and pitch
    private const val SOUND = "minecraft:block.note_block.pling"
    private const val SOUND_PITCH = 1.5f
}
