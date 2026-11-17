package org.blackaddons.core.ui.screen.actions

import org.blackaddons.api.actions.StepType
import org.blackaddons.api.ui.node.NodeProperty
import org.blackaddons.api.util.I18n
import org.blackaddons.core.actions.ActionDungeonEvent
import org.blackaddons.core.actions.ActionStep
import org.blackaddons.core.actions.ConditionType
import org.blackaddons.core.actions.ItemScope
import org.blackaddons.core.actions.TriggeredBy
import org.blackaddons.core.actions.WaitMode
import org.blackaddons.core.log.Log

internal val actionTr = I18n.scope("blackaddons.actions")

/** The type's name, or the name of the missing type. */
internal val ActionStep.typeName: String
    get() = type?.displayName ?: actionTr("step_type.missing", "Missing: %s", typeId)

// The type might come from an addon, so if it throws, only its own text and rows are lost.
internal fun ActionStep.summary(): String = typed { type, params -> type.summary(params) } ?: ""

internal fun ActionStep.typeProperties(): List<NodeProperty> = typed { type, params -> type.properties(params) } ?: emptyList()

@Suppress("UNCHECKED_CAST")
private fun <T> ActionStep.typed(block: (StepType<Any>, Any) -> T): T? {
    val type = type as StepType<Any>? ?: return null
    return try {
        block(type, params)
    } catch (t: Throwable) {
        Log.main.error("Action step type {} threw while describing a step", type.id, t)
        null
    }
}

internal val WaitMode.translatedName: String
    get() = actionTr("wait_mode.${name.lowercase()}", displayName)

internal val ConditionType.translatedName: String
    get() = actionTr("condition_type.${name.lowercase()}", displayName)

internal val ItemScope.translatedName: String
    get() = actionTr("item_scope.${name.lowercase()}", displayName)

internal val TriggeredBy.translatedName: String
    get() = actionTr("triggered_by.${name.lowercase()}", displayName)

internal val ActionDungeonEvent.translatedName: String
    get() = actionTr("dungeon_event.${name.lowercase()}", displayName)
