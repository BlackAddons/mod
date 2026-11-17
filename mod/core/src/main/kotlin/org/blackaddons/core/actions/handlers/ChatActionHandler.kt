package org.blackaddons.core.actions.handlers
import org.blackaddons.api.chat.PlayerMessage
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.chat.ChatReceiveEvent
import org.blackaddons.api.event.subscribe
import org.blackaddons.api.util.containsIgnoreCase
import org.blackaddons.core.actions.ActionStorage
import org.blackaddons.core.actions.execution.ActionRunner
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

internal object ChatActionHandler {

    private val patternCache = ConcurrentHashMap<String, Pattern?>()

    fun init() {
        EventBus.subscribe<ChatReceiveEvent> { onChatReceive(event) }
    }

    fun clearCache() {
        patternCache.clear()
    }

    internal fun compiled(raw: String): Pattern? =
        patternCache.computeIfAbsent(raw) { runCatching { Pattern.compile(it) }.getOrNull() }

    private fun onChatReceive(event: ChatReceiveEvent) {
        handleText(event.unformatted)
    }

    internal fun handleText(text: String) {
        if (text.isEmpty()) return

        var queuedActions = false
        val own by lazy { PlayerMessage.of(text)?.isOwn == true }
        for (action in ActionStorage.chatActions) {
            if (!action.enabled || action.pattern.isBlank() || !ActionStorage.inActiveGroup(ActionStorage.chatGroups, action)) continue
            if (action.ignoreOwnMessages && own) continue

            var matched = false
            var groups: Array<String>? = null

            if (action.isRegex) {
                val pattern = compiled(action.pattern) ?: continue

                val matcher = pattern.matcher(text)
                if (matcher.find()) {
                    matched = true
                    val count = matcher.groupCount()
                    groups = Array(count + 1) { i -> matcher.group(i) ?: "" }
                }
            } else {
                if (text.containsIgnoreCase(action.pattern)) {
                    matched = true
                    groups = null
                }
            }

            if (matched) {
                if (ActionRunner.run(action.actions, action, groups, clearPrevious = !queuedActions)) {
                    queuedActions = true
                }
            }
        }
    }
}
