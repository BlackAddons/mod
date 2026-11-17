package org.blackaddons.core.commands

import org.blackaddons.api.chat.ChatClick
import org.blackaddons.api.chat.ChatMessage
import org.blackaddons.api.commands.CommandContext
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.util.I18n
import org.blackaddons.core.actions.ActionStep
import org.blackaddons.core.chat.Brand
import org.blackaddons.core.chat.brand
import org.blackaddons.core.ui.screen.actions.typeName

internal val commandTr = I18n.scope("blackaddons.actions.command")

internal fun <T> CommandContext.listActions(collection: ActionCollection<T>) {
    val items = collection.items()
    if (items.isEmpty()) {
        replyAction(commandTr("none_in_profile", "No %s in the current profile.", collection.title), Theme.TEXT_MUTED)
        return
    }
    replyAction("${collection.title} (${items.size}):", Theme.ACCENT)
    items.forEachIndexed { index, item ->
        val reference = collection.id(item) ?: (index + 1).toString()
        val base = "$ACTION_COMMAND ${collection.path}"
        reply(
            ChatMessage.build {
                append(
                    "  ${index + 1}. [${commandTr(if (collection.enabled(item)) "on" else "off", if (collection.enabled(item)) "ON" else "OFF")}] ",
                    if (collection.enabled(item)) Brand.OK else Theme.TEXT_MUTED,
                )
                append(collection.name(item), Brand.BODY)
                append(" [${commandTr("edit", "Edit")}]", Theme.ACCENT, click = ChatClick.RunCommand("$base edit $reference"), hover = commandTr("edit", "Edit"))
                if (collection.ensureSteps != null) {
                    append(
                        " [${commandTr("steps", "Steps")}]",
                        Theme.ACCENT,
                        click = ChatClick.RunCommand("$base steps $reference"),
                        hover = commandTr("manage_steps", "Manage steps"),
                    )
                }
                if (collection.run != null) {
                    append(" [${commandTr("run", "Run")}]", Brand.OK, click = ChatClick.RunCommand("$base run $reference"), hover = commandTr("run_now", "Run now"))
                }
                append(
                    " [${commandTr("toggle", "Toggle")}]",
                    Theme.TEXT_MUTED,
                    click = ChatClick.RunCommand("$base toggle $reference"),
                    hover = commandTr("toggle_hint", "Enable or disable"),
                )
                append(
                    " [${commandTr("delete", "Delete")}]",
                    Brand.FAIL,
                    click = ChatClick.SuggestCommand("$base delete $reference confirm"),
                    hover = commandTr("delete_hint", "Click, then send to confirm deletion"),
                )
            },
        )
    }
}

internal fun <T> CommandContext.listSteps(collection: ActionCollection<T>, item: T, steps: List<ActionStep>) {
    if (steps.isEmpty()) {
        replyAction(commandTr("no_steps", "%s has no steps.", collection.name(item)), Theme.TEXT_MUTED)
        return
    }
    val reference = collection.reference(item)
    val base = "$ACTION_COMMAND ${collection.path} step"
    replyAction(commandTr("steps_count", "%s steps (%d):", collection.name(item), steps.size), Theme.ACCENT)
    steps.forEachIndexed { index, step ->
        reply(
            ChatMessage.build {
                append(
                    "  ${index + 1}. [${commandTr(if (step.enabled) "on" else "off", if (step.enabled) "ON" else "OFF")}] ",
                    if (step.enabled) Brand.OK else Theme.TEXT_MUTED,
                )
                append(step.typeName, Brand.BODY)
                append(" [${commandTr("edit", "Edit")}]", Theme.ACCENT, click = ChatClick.RunCommand("$base edit $reference ${index + 1}"))
                append(" [${commandTr("run", "Run")}]", Brand.OK, click = ChatClick.RunCommand("$base run $reference ${index + 1}"))
                append(
                    " [${commandTr("toggle", "Toggle")}]",
                    Theme.TEXT_MUTED,
                    click = ChatClick.RunCommand("$base toggle $reference ${index + 1}"),
                )
                append(
                    " [${commandTr("delete", "Delete")}]",
                    Brand.FAIL,
                    click = ChatClick.SuggestCommand("$base delete $reference ${index + 1} confirm"),
                )
            },
        )
    }
}

internal fun CommandContext.replyActionsHelp() {
    replyAction(commandTr("help", "Actions commands:"), Theme.ACCENT)
    replyAction("/b ac <chat|wp|shortcut|loc> <list|create|info|edit|steps|run|enable|disable|toggle|move|delete>")
    replyAction("/b ac <chat|wp|shortcut|loc> group <list|create|info|edit|assign|unassign|enable|disable|toggle|move|delete>")
    replyAction("/b ac <kind> step <list|create|edit|run|enable|disable|toggle|move|delete>")
    replyAction("/b ac profile <list|manage|select|create|copy|rename|delete|folder>; /b ac <status|stop>")
}

internal fun CommandContext.replyAction(text: String, color: java.awt.Color = Brand.BODY) {
    reply(
        ChatMessage.build {
            brand()
            append(text, color)
        },
    )
}
