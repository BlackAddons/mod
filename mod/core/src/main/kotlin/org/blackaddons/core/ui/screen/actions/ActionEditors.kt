package org.blackaddons.core.ui.screen.actions

import org.blackaddons.api.Platform
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.util.formatDecimals
import org.blackaddons.core.actions.*
import org.blackaddons.core.actions.execution.ActionExecutor
import org.blackaddons.core.actions.steps.BlockStep
import org.blackaddons.core.actions.steps.EndStep
import org.blackaddons.core.actions.steps.SendMessageStep
import org.blackaddons.core.features.impl.settings.Actions
import org.blackaddons.core.ui.screen.actions.graph.ActionGraphScreen
import org.blackaddons.core.ui.screen.actions.modals.*
import org.blackaddons.core.ui.screen.profile.ProfilesModal

internal enum class ActionsPage(val id: String, val label: String) {
    CHAT("chat", "Chat Actions"),
    WAYPOINT("waypoint", "Waypoint Actions"),
    SHORTCUT("shortcut", "Shortcuts"),
    LOCATION("location", "Location Actions"),
    ;

    fun groups(profile: ActionProfile): MutableList<ActionGroup> = when (this) {
        CHAT -> profile.chatGroups
        WAYPOINT -> profile.waypointGroups
        SHORTCUT -> profile.shortcutGroups
        LOCATION -> profile.locationGroups
    }
}

internal object ActionEditors {
    /** [page] or the last tab, in the view the Actions settings pick. */
    fun open(page: ActionsPage? = null) =
        if (Actions.opensGraph) ActionGraphScreen.open(page ?: ActionsScreen.lastPage, fromList = false) else ActionsScreen.open(page)

    fun manageProfiles(onProfileChanged: () -> Unit = {}) =
        UiManager.push(ProfilesModal(ActionProfiles, onProfileChanged))

    fun createChat(onSaved: () -> Unit = {}) {
        val action = ChatAction(pattern = "", enabled = true)
        ActionStorage.chatActions.add(action)
        UiManager.push(ChatActionModal(action, onSaved))
    }

    fun editChat(action: ChatAction, onSaved: () -> Unit = {}) =
        UiManager.push(ChatActionModal(action, onSaved))

    fun createWaypoint(onSaved: () -> Unit = {}) {
        val pos = Platform.player.pos
        val waypoint = WaypointDefinition(
            name = actionTr("waypoint.new_name", "New Waypoint"),
            x = pos.x.formatDecimals().toDouble(),
            y = pos.y.formatDecimals().toDouble(),
            z = pos.z.formatDecimals().toDouble(),
            enabled = true,
        )
        ActionStorage.waypoints.add(waypoint)
        ActionStorage.save()
        UiManager.push(WaypointModal(waypoint, onSaved))
    }

    fun editWaypoint(waypoint: WaypointDefinition, onSaved: () -> Unit = {}) =
        UiManager.push(WaypointModal(waypoint, onSaved))

    fun createGroup(into: MutableList<ActionGroup>, onSaved: () -> Unit = {}) =
        UiManager.push(ActionGroupModal(ActionGroup(name = actionTr("group.new_name", "New Group")), into, onSaved))

    fun editGroup(group: ActionGroup, onSaved: () -> Unit = {}) =
        UiManager.push(ActionGroupModal(group, onSaved = onSaved))

    fun createShortcut(onSaved: () -> Unit = {}) =
        UiManager.push(ShortcutActionModal(ShortcutAction(name = actionTr("shortcut.new_name", "New Shortcut"), enabled = true), isNew = true, onSaved = onSaved))

    fun editShortcut(shortcut: ShortcutAction, onSaved: () -> Unit = {}) =
        UiManager.push(ShortcutActionModal(shortcut, onSaved = onSaved))

    fun createLocation(onSaved: () -> Unit = {}) =
        UiManager.push(LocationActionModal(LocationAction(name = actionTr("location.new_name", "New Location Action"), enabled = true), isNew = true, onSaved = onSaved))

    fun editLocation(action: LocationAction, onSaved: () -> Unit = {}) =
        UiManager.push(LocationActionModal(action, onSaved = onSaved))

    fun openSteps(
        steps: MutableList<ActionStep>,
        onDone: () -> Unit = {},
        persist: () -> Unit = ActionStorage::save,
    ) = UiManager.push(ActionStepsModal(steps, onDone, persist))

    // Only added on save, so Cancel leaves nothing behind.
    fun createStep(steps: MutableList<ActionStep>, persist: () -> Unit = {}) {
        val step = ActionExecutor.newStep(SendMessageStep)
        UiManager.push(StepEditModal(step) {
            steps.add(step)
            if (step.type is BlockStep) steps.add(ActionExecutor.newStep(EndStep))
            persist()
        })
    }

    fun editStep(step: ActionStep, persist: () -> Unit = {}) =
        UiManager.push(StepEditModal(step, onSave = persist))

    fun waypointSteps(waypoint: WaypointDefinition): MutableList<ActionStep> {
        val action = waypoint.actions.firstOrNull() ?: WaypointAction().also {
            waypoint.actions.add(it)
            ActionStorage.save()
        }
        return action.actions
    }
}
