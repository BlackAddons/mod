package org.blackaddons.core.features.impl.chat

import org.blackaddons.api.event.EventPriority
import org.blackaddons.api.event.chat.ChatMessageEvent
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.feature.settings.impl.EntryOption
import java.util.regex.PatternSyntaxException

object ChatVisualFilters : Feature(
    description = "Hides chat messages that match your patterns.",
    name = "Chat Visual Filters",
) {

    private val match = EntryOption("match", listOf(CONTAINS, STARTS_WITH, EXACT, REGEX))
    private val case = EntryOption("case", listOf(ANY_CASE, MATCH_CASE), label = "Aa")

    private val patterns by list("Patterns", placeholder = "Pattern", itemMaxLength = PATTERN_MAX_LENGTH)
        .withEntryOptions(match, case)
        .withDescription("Messages matching any of these are hidden. Each pattern has its own match mode and case")

    private var compiledFor: Pair<List<String>, Map<String, Map<String, String>>>? = null
    private var compiled: List<Regex> = emptyList()

    override fun init() {
        register<ChatMessageEvent>(EventPriority.HIGH) {
            if (current().any { it.containsMatchIn(event.unformatted) }) event.cancelled = true
        }
    }

    private fun current(): List<Regex> {
        val key = patterns.value to patterns.entryChoices
        if (key != compiledFor) {
            compiledFor = key
            compiled = patterns.value.mapNotNull { pattern ->
                regexFor(pattern, patterns.choiceOf(pattern, match), patterns.choiceOf(pattern, case) == MATCH_CASE)
            }
        }
        return compiled
    }

    /** Null for an invalid regex, which then hides nothing. */
    internal fun regexFor(pattern: String, match: String, caseSensitive: Boolean): Regex? {
        val source = when (match) {
            STARTS_WITH -> "\\A" + Regex.escape(pattern)
            EXACT -> "\\A" + Regex.escape(pattern) + "\\z"
            REGEX -> pattern
            else -> Regex.escape(pattern)
        }
        return try {
            Regex(source, if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE))
        } catch (_: PatternSyntaxException) {
            null
        }
    }

    internal const val CONTAINS = "Contains"
    internal const val STARTS_WITH = "Starts With"
    internal const val EXACT = "Exact"
    internal const val REGEX = "Regex"
    private const val ANY_CASE = "Any Case"
    private const val MATCH_CASE = "Match Case"
    private const val PATTERN_MAX_LENGTH = 256
}
