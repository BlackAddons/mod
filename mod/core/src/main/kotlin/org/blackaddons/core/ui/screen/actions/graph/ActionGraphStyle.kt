package org.blackaddons.core.ui.screen.actions.graph

import org.blackaddons.api.actions.StepCategory
import org.blackaddons.api.ui.node.GraphNode
import org.blackaddons.core.actions.ActionGroup
import org.blackaddons.core.actions.steps.BlockStep
import org.blackaddons.core.ui.screen.actions.actionTr
import org.blackaddons.core.ui.screen.actions.typeName
import java.awt.Color

internal object ActionGraphStyle {

    fun style(node: GraphNode) {
        when (val payload = node.payload) {
            is TriggerPayload -> {
                val ref = payload.ref
                node.toggleable = true
                node.dimmed = !ref.enabled
                node.headerColor = triggerColor(ref)
                node.title = triggerTitle(ref)
                node.properties = ActionGraphProperties.of(ref)
            }
            is StepPayload -> {
                val step = payload.step
                node.dimmed = !step.enabled
                node.toggleable = true
                node.headerColor = stepColor(step.type?.category)
                node.title = step.typeName
                node.properties = ActionGraphProperties.of(step)
                node.warning = when {
                    step.type == null -> actionTr("graph.missing", "addon missing")
                    step.type is BlockStep && payload.end == null -> actionTr("graph.unclosed", "open block")
                    else -> null
                }
            }
        }
    }

    fun triggerTitle(ref: TriggerRef): String = when (ref) {
        is TriggerRef.Chat -> actionTr("graph.trigger.chat", "Chat: %s", ref.action.pattern.ifBlank { actionTr("graph.trigger.no_pattern", "(no pattern)") })
        is TriggerRef.Waypoint -> actionTr("graph.trigger.waypoint", "Waypoint: %s", ref.waypoint.name) +
            if (ref.waypoint.actions.size > 1) " #${ref.index + 1}" else ""
        is TriggerRef.Shortcut -> actionTr("graph.trigger.shortcut", "Shortcut: %s", ref.shortcut.name)
        is TriggerRef.Location -> actionTr("graph.trigger.location", "Location: %s", ref.action.name)
    }

    private fun triggerColor(ref: TriggerRef): Color = when (ref) {
        is TriggerRef.Chat -> CHAT_COLOR
        is TriggerRef.Waypoint -> WAYPOINT_COLOR
        is TriggerRef.Shortcut -> SHORTCUT_COLOR
        is TriggerRef.Location -> LOCATION_COLOR
    }

    private fun stepColor(category: StepCategory?): Color = when (category) {
        StepCategory.CONTROL -> CONTROL_COLOR
        StepCategory.WAIT -> WAIT_COLOR
        StepCategory.MOVEMENT -> MOVEMENT_COLOR
        StepCategory.ITEM -> ITEM_COLOR
        StepCategory.OUTPUT -> OUTPUT_COLOR
        null -> DISABLED_GROUP_COLOR
    }

    fun frameColor(group: ActionGroup, index: Int): Color =
        if (group.enabled) groupColor(index) else DISABLED_GROUP_COLOR

    fun groupColor(index: Int): Color = GROUP_COLORS[index % GROUP_COLORS.size]

    private val CHAT_COLOR = Color(0x3E6A9E)
    private val WAYPOINT_COLOR = Color(0x3F7F5A)
    private val SHORTCUT_COLOR = Color(0x8F6530)
    private val LOCATION_COLOR = Color(0x2F7A80)
    private val CONTROL_COLOR = Color(0x6B4C9A)
    private val WAIT_COLOR = Color(0x55607A)
    private val MOVEMENT_COLOR = Color(0x8A4B4B)
    private val ITEM_COLOR = Color(0x8A7440)
    private val OUTPUT_COLOR = Color(0x3D6F80)
    private val GROUP_COLORS = listOf(CHAT_COLOR, WAYPOINT_COLOR, SHORTCUT_COLOR, CONTROL_COLOR, ITEM_COLOR, LOCATION_COLOR)
    private val DISABLED_GROUP_COLOR = Color(0x5A5E68)
}
