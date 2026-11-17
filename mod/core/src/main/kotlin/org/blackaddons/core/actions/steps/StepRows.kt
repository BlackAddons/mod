package org.blackaddons.core.actions.steps

import org.blackaddons.api.skyblock.dungeon.DungeonClass
import org.blackaddons.api.skyblock.dungeon.DungeonFloor
import org.blackaddons.api.ui.node.NodeProperty
import org.blackaddons.api.ui.node.NodeRows
import org.blackaddons.api.util.equalsIgnoreCase
import org.blackaddons.api.util.formatDecimals
import org.blackaddons.core.actions.ActionCondition
import org.blackaddons.core.actions.ConditionType
import org.blackaddons.core.actions.ItemScope
import org.blackaddons.core.actions.LocationFilterProvider
import org.blackaddons.core.gamedata.SkyblockAreas
import org.blackaddons.core.ui.screen.actions.actionTr
import org.blackaddons.core.ui.screen.actions.fields.LocationFilterFields
import org.blackaddons.core.ui.screen.actions.fields.dropUnreachable
import org.blackaddons.core.ui.screen.actions.translatedName

/** A row label of the actions editor. */
internal fun prop(key: String, fallback: String): String = actionTr("graph.prop.$key", fallback)

/** Condition rows of If and "repeat while". [condition] creates the condition on the first write. */
internal fun NodeRows.condition(read: () -> ActionCondition?, write: (ActionCondition) -> Unit) {
    val current = { read() ?: NO_CONDITION }
    val edit = { read() ?: ActionCondition().also(write) }
    choice(prop("condition", "Condition"), ConditionType.entries.map { it.translatedName }, { current().type.ordinal }) {
        edit().apply { type = ConditionType.entries[it] }.adoptShownClass()
    }
    toggle(prop("not", "Not"), { current().negate }, { edit().negate = it })
    when (current().type) {
        ConditionType.LOCATION -> locationFilter(current, edit)
        ConditionType.NEAR -> {
            // Only a stored condition can be NEAR, so this creates nothing.
            val near = edit()
            coordinates(near::x, near::y, near::z)
            number(actionTr("radius", "Radius"), near::radius, 0.5, MIN_CONDITION_RADIUS, decimals = 1)
        }
        ConditionType.ITEM -> {
            text(prop("item", "Item"), { current().item }, { edit().item = it.trim() }, actionTr("condition.item_placeholder", "SkyBlock id or name, e.g. SPIRIT_LEAP"))
            choice(prop("scope", "Scope"), ItemScope.entries.map { it.translatedName }, { current().itemScope.ordinal }) { edit().itemScope = ItemScope.entries[it] }
        }
        ConditionType.DUNGEON_CLASS -> choice(
            prop("class", "Class"), CLASSES.map { it.displayName },
            { CLASSES.indexOfFirst { it.name.equalsIgnoreCase(current().dungeonClass) }.coerceAtLeast(0) },
        ) { edit().dungeonClass = CLASSES[it].name }
    }
}

/** Area, then floor, boss and phase, as far as each of them can still narrow the area down. */
internal fun NodeRows.locationFilter(read: () -> LocationFilterProvider, write: () -> LocationFilterProvider) {
    add(
        NodeProperty.Flags(
            prop("area", "Area"), SkyblockAreas.allNames, { read().areaFilters.toSet() },
            { chosen -> write().apply { areaFilters = chosen.toMutableList() }.dropUnreachable() },
            none = actionTr("any_island", "Any Island (All)"),
        ),
    )
    val filter = read()
    if (filter.areaFilters.none { it.equalsIgnoreCase(LocationFilterFields.CATACOMBS) }) return
    choice(prop("floor", "Floor"), LocationFilterFields.FLOOR_OPTIONS, { read().floorFilter?.let { it.ordinal + 1 } ?: 0 }) {
        write().apply { floorFilter = DungeonFloor.entries.getOrNull(it - 1) }.dropUnreachable()
    }
    if (filter.floorFilter == DungeonFloor.ENTRANCE) return
    val any = actionTr("any", "ANY")
    choice(
        prop("boss", "Boss"), listOf(any, actionTr("yes", "YES"), actionTr("no", "NO")),
        { when (read().inBossFilter) { null -> 0; true -> 1; false -> 2 } },
    ) { write().inBossFilter = when (it) { 1 -> true; 2 -> false; else -> null } }
    if (filter.floorFilter?.isF7orM7 == false) return
    val phases = listOf(any) + (1..LocationFilterFields.LAST_PHASE).map { "P$it" }
    choice(prop("phase", "Phase"), phases, { read().phaseFilter?.coerceIn(0, LocationFilterFields.LAST_PHASE) ?: 0 }) { write().phaseFilter = it.takeIf { it > 0 } }
}

/**
 * A class condition without a class meant the class the editor showed, the first one. It used to save as blank and was
 * never met.
 */
internal fun ActionCondition.adoptShownClass() {
    if (type == ConditionType.DUNGEON_CLASS && dungeonClass.isBlank()) dungeonClass = CLASSES.first().name
}

internal fun ActionCondition?.describe(): String {
    val c = this ?: return ""
    val what = when (c.type) {
        ConditionType.LOCATION -> listOfNotNull(
            c.areaFilters.firstOrNull(), c.floorFilter?.label,
            c.inBossFilter?.let { if (it) actionTr("boss", "Boss") else actionTr("no_boss", "No Boss") },
            c.phaseFilter?.let { "P$it" },
        ).joinToString(" ").ifEmpty { c.type.translatedName }
        ConditionType.NEAR -> "X:${c.x.toInt()} Y:${c.y.toInt()} Z:${c.z.toInt()} (r=${c.radius.formatDecimals(1)}m)"
        ConditionType.ITEM -> "${c.item} ${c.itemScope.translatedName.lowercase()}"
        ConditionType.DUNGEON_CLASS -> c.dungeonClass.lowercase().replaceFirstChar(Char::uppercase)
    }
    return if (c.negate) actionTr("steps.not", "not %s", what) else what
}

private const val MIN_CONDITION_RADIUS = 0.1
private val NO_CONDITION = ActionCondition()
private val CLASSES = DungeonClass.entries.filter { it != DungeonClass.EMPTY }
