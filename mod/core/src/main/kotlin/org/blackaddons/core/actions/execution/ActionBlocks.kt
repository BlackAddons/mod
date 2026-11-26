package org.blackaddons.core.actions.execution

import org.blackaddons.api.Platform
import org.blackaddons.api.player.Hotbar
import org.blackaddons.api.skyblock.dungeon.Dungeon
import org.blackaddons.api.util.containsIgnoreCase
import org.blackaddons.api.util.equalsIgnoreCase
import org.blackaddons.api.world.ItemInfo
import org.blackaddons.core.actions.*
import org.blackaddons.core.actions.steps.BlockStep
import org.blackaddons.core.actions.steps.ElseStep
import org.blackaddons.core.actions.steps.EndStep
import org.blackaddons.core.actions.steps.IfStep

internal object ActionBlocks {

    /** Returns the END of the block at [start]. An unclosed block runs to the end of [steps]. */
    fun endOf(steps: List<ActionStep>, start: Int): Int {
        var depth = 0
        for (i in start + 1 until steps.size) {
            val type = steps[i].type
            if (type is BlockStep) depth++
            if (type === EndStep) {
                if (depth == 0) return i
                depth--
            }
        }
        return steps.size
    }

    /** The ELSE of the If at [start], or null. */
    fun elseOf(steps: List<ActionStep>, start: Int): Int? {
        if (steps[start].type !== IfStep) return null
        val end = endOf(steps, start)
        var depth = 0
        for (i in start + 1 until end) {
            val type = steps[i].type
            when {
                type is BlockStep -> depth++
                type === EndStep -> depth--
                type === ElseStep && depth == 0 -> return i
            }
        }
        return null
    }

    /** The block step [pos] sits in directly, or null at the top. */
    fun openerOf(steps: List<ActionStep>, pos: Int): Int? {
        var depth = 0
        for (i in pos - 1 downTo 0) {
            val type = steps[i].type
            if (type === EndStep) depth++
            if (type is BlockStep) {
                if (depth == 0) return i
                depth--
            }
        }
        return null
    }

    /**
     * Indent depth of each step. An END and an ELSE have the depth of their opener. An unmatched END, and an ELSE that
     * isn't the first one directly in an If, get -1.
     */
    fun depths(steps: List<ActionStep>): IntArray {
        val result = IntArray(steps.size)
        // One entry per open block: whether it's an If that can still take an ELSE.
        val open = ArrayList<Boolean>()
        for ((i, step) in steps.withIndex()) {
            when {
                step.type === EndStep -> result[i] = if (open.isEmpty()) {
                    -1
                } else {
                    open.removeAt(open.lastIndex)
                    open.size
                }
                step.type === ElseStep -> result[i] = if (open.lastOrNull() == true) {
                    open[open.size - 1] = false
                    open.size - 1
                } else {
                    -1
                }
                else -> {
                    result[i] = open.size
                    if (step.type is BlockStep) open += step.type === IfStep
                }
            }
        }
        return result
    }
}

internal object ActionConditions {

    /** A missing condition counts as met. */
    fun met(condition: ActionCondition?): Boolean {
        val c = condition ?: return true
        val held = when (c.type) {
            ConditionType.LOCATION -> matchesLocationFilter(c.areaFilters, c.inDungeonFilter, c.floorFilter, c.inBossFilter, c.phaseFilter)
            ConditionType.NEAR -> near(c)
            ConditionType.ITEM -> c.item.isNotBlank() && carries(c)
            ConditionType.DUNGEON_CLASS -> Dungeon.team.self?.dungeonClass?.name.equalsIgnoreCase(c.dungeonClass)
        }
        return held != c.negate
    }

    private fun near(c: ActionCondition): Boolean {
        if (!Platform.player.isInGame) return false
        val dx = Platform.player.x - c.x
        val dy = Platform.player.y - c.y
        val dz = Platform.player.z - c.z
        return dx * dx + dy * dy + dz * dz <= c.radius * c.radius
    }

    private fun carries(c: ActionCondition): Boolean {
        val matches = { item: ItemInfo -> item.skyblockId.equalsIgnoreCase(c.item) || item.name.containsIgnoreCase(c.item) }
        return when (c.itemScope) {
            ItemScope.HAND -> Platform.player.mainHandItem?.let(matches) == true
            ItemScope.HOTBAR -> Hotbar.find(match = matches) != null
        }
    }
}
