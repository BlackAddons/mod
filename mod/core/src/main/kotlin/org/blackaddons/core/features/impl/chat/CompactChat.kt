package org.blackaddons.core.features.impl.chat

import org.blackaddons.api.Platform
import org.blackaddons.api.chat.ChatLine
import org.blackaddons.api.chat.ChatMessage
import org.blackaddons.api.event.EventPriority
import org.blackaddons.api.event.chat.ChatLineAddedEvent
import org.blackaddons.api.event.chat.ChatMessageEvent
import org.blackaddons.api.feature.Feature
import java.awt.Color

/** Ported from compact-chat (MIT, (c) 2025 Caoimhe Byrne). */
object CompactChat : Feature(
    description = "Stacks repeated chat messages into one line with a counter.",
    name = "Compact Chat",
) {

    private val consecutiveOnly by boolean("Consecutive Only", false)
        .withDescription("Only stack a message that repeats the newest one")

    private class Stack(val count: Int, val line: ChatLine)

    private val stacks = object : LinkedHashMap<String, Stack>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Stack>) = size > MAX_TRACKED
    }

    private var pending: Pair<String, Int>? = null

    override fun init() {
        register<ChatMessageEvent>(EventPriority.LOWEST) { stack(event) }
        register<ChatLineAddedEvent> { stored(event.line) }
    }

    internal fun stack(event: ChatMessageEvent) {
        pending = null
        if (event.cancelled || event.raw.isBlank() || SEPARATORS.any { it in event.raw }) return

        val previous = stacks[event.raw]?.takeIf { stack ->
            val lines = Platform.chat.lines
            if (consecutiveOnly.value) lines.firstOrNull() === stack.line else lines.any { it === stack.line }
        }
        val count = (previous?.count ?: 0) + 1
        pending = event.raw to count
        if (previous == null) return

        previous.line.remove()
        event.suffix = ChatMessage.build { append(" ($count)", COUNTER_COLOR) }
    }

    internal fun stored(line: ChatLine) {
        val (raw, count) = pending ?: return
        pending = null
        if (line.raw == raw) stacks[raw] = Stack(count, line)
    }

    // IIRC there are some more separators, come back to this later.
    private val SEPARATORS = listOf("-----", "▬▬▬", "=====") 
    private val COUNTER_COLOR = Color(0xAAAAAA)
    // Probably should be lower?
    private const val MAX_TRACKED = 256
}
