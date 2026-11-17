package org.blackaddons.core.commands

import org.blackaddons.api.Platform
import org.blackaddons.api.commands.ArgType
import org.blackaddons.api.commands.CommandBuilder
import org.blackaddons.core.actions.ActionGroup
import org.blackaddons.core.actions.ActionStorage
import org.blackaddons.core.actions.ChatAction
import org.blackaddons.core.actions.Groupable
import org.blackaddons.core.actions.LocationAction
import org.blackaddons.core.actions.ShortcutAction
import org.blackaddons.core.actions.WaypointDefinition
import org.blackaddons.core.actions.execution.ActionRunner
import org.blackaddons.core.chat.Brand
import org.blackaddons.core.ui.screen.actions.ActionEditors
import org.blackaddons.core.ui.screen.actions.ActionsPage

internal fun chatCollection() = ActionCollection(
    path = "chat",
    title = commandTr("chat_actions", "Chat Actions"),
    page = ActionsPage.CHAT,
    items = { ActionStorage.chatActions },
    name = { it.pattern.ifBlank { commandTr("empty_pattern", "<Empty Pattern>") } },
    details = { commandTr("chat_details", "regex=%s, steps=%d", it.isRegex, it.actions.size) },
    enabled = ChatAction::enabled,
    setEnabled = { action, value -> action.enabled = value },
    create = { openLater { ActionEditors.createChat() } },
    edit = { action -> openLater { ActionEditors.editChat(action) } },
    save = ActionStorage::save,
    delete = { ActionStorage.chatActions.removeIdentity(it).also { removed -> if (removed) ActionStorage.save() } },
    steps = ChatAction::actions,
    ensureSteps = ChatAction::actions,
    run = { ActionRunner.run(it.actions, it) },
)

internal fun waypointCollection() = ActionCollection(
    path = "wp",
    title = commandTr("waypoints", "Waypoints"),
    page = ActionsPage.WAYPOINT,
    items = { ActionStorage.waypoints },
    name = { it.name.ifBlank { commandTr("waypoint", "Waypoint") } },
    id = { it.id.toString() },
    details = {
        commandTr(
            "waypoint_details", "x=%s, y=%s, z=%s, actions=%d",
            it.x, it.y, it.z, it.actions.size,
        )
    },
    enabled = WaypointDefinition::enabled,
    setEnabled = { waypoint, value -> waypoint.enabled = value },
    create = {
        if (!Platform.player.isInGame) {
            replyAction(commandTr("join_for_waypoint", "Join a world before creating a waypoint from your current position."), Brand.FAIL)
        } else {
            openLater { ActionEditors.createWaypoint() }
        }
    },
    edit = { waypoint -> openLater { ActionEditors.editWaypoint(waypoint) } },
    save = ActionStorage::save,
    delete = {
        ActionStorage.waypoints.removeIdentity(it).also { removed ->
            if (removed) ActionStorage.save()
        }
    },
    steps = { it.actions.firstOrNull()?.actions },
    ensureSteps = ActionEditors::waypointSteps,
    run = ActionRunner::runWaypoint,
    waypoint = { it },
)

internal fun shortcutCollection() = ActionCollection(
    path = "shortcut",
    title = commandTr("shortcuts", "Shortcuts"),
    page = ActionsPage.SHORTCUT,
    items = { ActionStorage.shortcuts },
    name = { it.name.ifBlank { it.alias.ifBlank { commandTr("shortcut", "Shortcut") } } },
    id = { it.id.toString() },
    details = { commandTr("shortcut_details", "alias=/%s, bound=%s, steps=%d", it.alias, it.isBound, it.actions.size) },
    enabled = ShortcutAction::enabled,
    setEnabled = { shortcut, value -> shortcut.enabled = value },
    create = { openLater { ActionEditors.createShortcut() } },
    edit = { shortcut -> openLater { ActionEditors.editShortcut(shortcut) } },
    save = ActionStorage::save,
    delete = {
        ActionStorage.shortcuts.removeIdentity(it).also { removed ->
            if (removed) ActionStorage.save()
        }
    },
    steps = ShortcutAction::actions,
    ensureSteps = ShortcutAction::actions,
    run = { ActionRunner.run(it.actions) },
)

internal fun locationCollection() = ActionCollection(
    path = "loc",
    title = commandTr("location_actions", "Location Actions"),
    page = ActionsPage.LOCATION,
    items = { ActionStorage.locationActions },
    name = { it.name.ifBlank { commandTr("location_trigger", "Location Trigger") } },
    id = { it.id.toString() },
    details = {
        commandTr(
            "location_details", "areas=%s, delay=%ss, cooldown=%ss, steps=%d",
            it.areaFilters.joinToString().ifBlank { commandTr("any", "any") },
            it.delaySeconds, it.cooldownSeconds, it.actions.size,
        )
    },
    enabled = LocationAction::enabled,
    setEnabled = { action, value -> action.enabled = value },
    create = { openLater { ActionEditors.createLocation() } },
    edit = { action -> openLater { ActionEditors.editLocation(action) } },
    save = ActionStorage::save,
    delete = {
        ActionStorage.locationActions.removeIdentity(it).also { removed ->
            if (removed) ActionStorage.save()
        }
    },
    steps = LocationAction::actions,
    ensureSteps = LocationAction::actions,
    run = { ActionRunner.run(it.actions) },
)

/** Under `/b ac <path> group`. */
internal fun groupCollection(path: String, title: String, page: ActionsPage) = ActionCollection(
    path = "$path group",
    title = title,
    page = page,
    items = { page.groups(ActionStorage.activeProfile) },
    name = { it.name.ifBlank { commandTr("unnamed_group", "Unnamed Group") } },
    id = { it.id.toString() },
    details = {
        commandTr(
            "group_details", "areas=%s, floor=%s",
            it.areaFilters.joinToString().ifBlank { commandTr("any", "any") },
            it.floorFilter?.label ?: commandTr("any", "any"),
        )
    },
    enabled = ActionGroup::enabled,
    setEnabled = { group, value -> group.enabled = value },
    create = { openLater { ActionEditors.createGroup(page.groups(ActionStorage.activeProfile)) } },
    edit = { group -> openLater { ActionEditors.editGroup(group) } },
    save = ActionStorage::save,
    delete = {
        val exists = page.groups(ActionStorage.activeProfile).any { group -> group === it }
        if (exists) ActionStorage.deleteGroup(it.id)
        exists
    },
)

internal fun <T : Groupable> CommandBuilder.registerGroupBranches(
    groups: ActionCollection<ActionGroup>,
    items: ActionCollection<T>,
) {
    registerCollection(groups)
    subcommand("assign") {
        arg("item", ArgType.StrArg) {
            suggests { items.suggestions(finalArgument = false) }
            arg("group", ArgType.GreedyStrArg) {
                suggests { groups.suggestions(finalArgument = true) }
                execute {
                    val item = resolve(items, str("item")) ?: return@execute
                    val group = resolve(groups, str("group")) ?: return@execute
                    item.groupId = group.id
                    items.save()
                    replyAction(commandTr("assigned_group", "Assigned %s to %s.", items.name(item), group.name), Brand.OK)
                }
            }
        }
    }
    subcommand("unassign") {
        arg("item", ArgType.GreedyStrArg) {
            suggests { items.suggestions(finalArgument = true) }
            execute {
                val item = resolve(items, str("item")) ?: return@execute
                item.groupId = null
                items.save()
                replyAction(commandTr("removed_group", "Removed %s from its group.", items.name(item)), Brand.OK)
            }
        }
    }
}
