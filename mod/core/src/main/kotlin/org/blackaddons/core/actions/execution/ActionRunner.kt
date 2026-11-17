package org.blackaddons.core.actions.execution

import org.blackaddons.core.actions.ActionFeedbackProvider
import org.blackaddons.core.actions.ActionStep
import org.blackaddons.core.actions.WaypointDefinition
internal object ActionRunner {
    fun run(
        steps: List<ActionStep>,
        feedback: ActionFeedbackProvider? = null,
        groups: Array<String>? = null,
        waypoint: WaypointDefinition? = null,
        clearPrevious: Boolean = true,
        defaultNotificationTitle: String = "Action Triggered",
        player: String? = null,
    ): Boolean {
        if (feedback != null) {
            ActionFeedbackDispatcher.dispatch(feedback, groups, defaultNotificationTitle, player)
        }
        if (steps.none { it.runsFor(player) }) return false
        ActionExecutor.execute(steps, groups, waypoint, clearPrevious, player)
        return true
    }

    fun runWaypoint(waypoint: WaypointDefinition): Boolean {
        var queued = false
        for (action in waypoint.actions) {
            if (!action.enabled) continue
            if (run(
                    steps = action.actions,
                    feedback = action,
                    waypoint = waypoint,
                    clearPrevious = !queued,
                    defaultNotificationTitle = waypoint.name.ifBlank { "Waypoint Action" },
                )) {
                queued = true
            }
        }
        return queued
    }
}
