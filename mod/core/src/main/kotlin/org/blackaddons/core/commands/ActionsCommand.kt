package org.blackaddons.core.commands

import org.blackaddons.api.Platform
import org.blackaddons.api.commands.CommandBuilder
import org.blackaddons.api.commands.subcommands
import org.blackaddons.core.actions.Groupable
import org.blackaddons.core.actions.execution.ActionExecutor
import org.blackaddons.core.chat.Brand
import org.blackaddons.core.ui.screen.actions.ActionEditors

internal const val ACTION_COMMAND = "blackaddons ac"

internal fun CommandBuilder.registerActionsBranches() {
    execute { openLater { ActionEditors.open() } }

    subcommand("help") { execute { replyActionsHelp() } }
    subcommand("status") {
        execute {
            val status = ActionExecutor.status()
            val active = status.activeType?.displayName ?: commandTr("none", "none")
            val state = commandTr(if (status.busy) "busy" else "idle", if (status.busy) "busy" else "idle")
            replyAction(commandTr("status", "Execution: %s; active: %s; queued: %d.", state, active, status.queuedSteps))
        }
    }
    subcommand("stop") {
        execute {
            ActionExecutor.cancel()
            replyAction(commandTr("stopped", "Stopped the active action and cleared its queue."), Brand.OK)
        }
    }

    subcommands("profile", "profiles") { registerProfileBranches() }
    val chat = chatCollection()
    subcommand("chat") { registerGrouped(chat, commandTr("chat_groups", "Chat Groups")) }
    val waypoints = waypointCollection()
    subcommands("wp", "waypoint", "waypoints") { registerGrouped(waypoints, commandTr("waypoint_groups", "Waypoint Groups")) }
    val shortcuts = shortcutCollection()
    subcommands("sc", "shortcut", "shortcuts") { registerGrouped(shortcuts, commandTr("shortcut_groups", "Shortcut Groups")) }
    val locations = locationCollection()
    subcommands("loc", "location", "locations") { registerGrouped(locations, commandTr("location_groups", "Location Groups")) }
}

private fun <T : Groupable> CommandBuilder.registerGrouped(items: ActionCollection<T>, groupsTitle: String) {
    registerCollection(items)
    subcommands("group", "groups") { registerGroupBranches(groupCollection(items.path, groupsTitle, items.page), items) }
}

internal fun openLater(open: () -> Unit) {
    Platform.scheduler.schedule(0, open)
}
