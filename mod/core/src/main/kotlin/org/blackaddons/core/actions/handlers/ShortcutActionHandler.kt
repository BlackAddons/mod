package org.blackaddons.core.actions.handlers
import org.blackaddons.api.Platform
import org.blackaddons.api.commands.DynamicAlias
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.EventPriority
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.event.chat.ChatSendEvent
import org.blackaddons.api.event.input.KeyInputEvent
import org.blackaddons.api.event.input.MouseClickEvent
import org.blackaddons.api.event.subscribe
import org.blackaddons.api.input.InputType
import org.blackaddons.api.util.cleanCommand
import org.blackaddons.api.util.equalsIgnoreCase
import org.blackaddons.api.util.splitWhitespace
import org.blackaddons.core.actions.ActionStorage
import org.blackaddons.core.actions.ShortcutAction
import org.blackaddons.core.actions.execution.ActionExecutor
import org.blackaddons.core.actions.steps.SendMessageStep
import org.blackaddons.core.log.Log

internal object ShortcutActionHandler {

    fun init() {
        EventBus.subscribe<ChatSendEvent>(EventPriority.HIGHEST) { onChatSend(event) }
        EventBus.subscribe<KeyInputEvent> { onKey(event) }
        EventBus.subscribe<MouseClickEvent> { onMouse(event) }
        EventBus.subscribe<WorldChangeEvent> { syncAliases() }
        syncAliases()
    }

    fun syncAliases() {
        runCatching {
            val list = ActionStorage.shortcuts
                .filter { sc -> sc.enabled && sc.alias.isNotBlank() && ActionStorage.shortcutGroups.none { it.id == sc.groupId && !it.enabled } }
                .mapNotNull { sc ->
                    val aliasName = sc.alias.cleanCommand()
                    if (aliasName.isEmpty()) return@mapNotNull null
                    val targetMsg = sc.actions.firstNotNullOfOrNull { it.paramsOf(SendMessageStep) }?.message?.trim() ?: ""
                    val targetCmd = if (targetMsg.startsWith("/")) targetMsg.cleanCommand() else ""
                    DynamicAlias(
                        name = aliasName,
                        targetCommand = targetCmd,
                    )
                }
            Platform.commands.syncDynamicAliases(list)
        }.onFailure { Log.main.error("Could not sync shortcut aliases", it) }
    }

    private fun onChatSend(event: ChatSendEvent) {
        if (!event.isCommand) return
        val fullCommand = event.message.cleanCommand()
        if (fullCommand.isEmpty()) return

        val aliasName = fullCommand.substringBefore(' ')
        val shortcut = ActionStorage.shortcuts.firstOrNull {
            it.enabled && it.alias.isNotBlank() && it.alias.cleanCommand().equalsIgnoreCase(aliasName) &&
                ActionStorage.inActiveGroup(ActionStorage.shortcutGroups, it)
        } ?: return

        event.cancelled = true
        val rawArgs = if (fullCommand.contains(' ')) fullCommand.substringAfter(' ').trim() else ""
        val tokens = if (rawArgs.isEmpty()) emptyList() else rawArgs.splitWhitespace()
        val groups = arrayOf(rawArgs, *tokens.toTypedArray())

        val actionsToRun = if (rawArgs.isNotBlank()) {
            shortcut.actions.map { step ->
                val message = step.paramsOf(SendMessageStep)?.message
                if (message != null && !message.contains("{args}") && !message.contains("{0}") &&
                    (1..tokens.size).none { message.contains("{$it}") }
                ) {
                    step.copy().also { it.paramsOf(SendMessageStep)?.message = "$message $rawArgs".trim() }
                } else {
                    step
                }
            }
        } else {
            shortcut.actions
        }
        ActionExecutor.execute(actionsToRun, groups)
    }

    private fun onKey(event: KeyInputEvent) {
        // A step that presses its own shortcut key would trigger itself forever.
        if (!event.isPress || event.synthetic) return
        handleKeybind(InputType.KEYBOARD, event.key)
    }

    private fun onMouse(event: MouseClickEvent) {
        if (!event.isPress) return
        handleKeybind(InputType.MOUSE, event.button)
    }

    private fun handleKeybind(type: InputType, code: Int) {
        val matches = ActionStorage.shortcuts.filter { shortcut ->
            shortcut.enabled &&
                ActionStorage.inActiveGroup(ActionStorage.shortcutGroups, shortcut) &&
                (!shortcut.onlyInGame || !Platform.screenHost.anyScreenOpen) &&
                shortcut.key.matches(type, code)
        }
        ActionExecutor.executeAll(matches.map(ShortcutAction::actions))
    }
}
