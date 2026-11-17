package org.blackaddons.core.ui.screen.actions.fields
import org.blackaddons.api.skyblock.LocationUtils
import org.blackaddons.api.skyblock.dungeon.DungeonFloor
import org.blackaddons.api.ui.layout.Align
import org.blackaddons.api.ui.layout.LayoutBuilder
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.WidgetHost
import org.blackaddons.api.ui.widget.dropdown.Dropdown
import org.blackaddons.api.ui.widget.dropdown.MultiSelectDropdown
import org.blackaddons.api.util.equalsIgnoreCase
import org.blackaddons.core.actions.LocationFilterProvider
import org.blackaddons.core.gamedata.SkyblockAreas
import org.blackaddons.core.ui.screen.actions.actionTr

/**
 * Island plus Catacombs floor/boss/phase filters. Location triggers and waypoint groups share them. Edits a copy:
 * [readFrom] fills it, [writeTo] commits.
 */
internal class LocationFilterFields {

    private var inBoss: Boolean? = null
    private var phase: Int? = null

    private val areaDropdown = MultiSelectDropdown(placeholder = actionTr("any_island", "Any Island (All)")).apply {
        items = SkyblockAreas.allNames.map { MultiSelectDropdown.Item(it, it) }
        selectedFormatter = { ids, itemList ->
            when {
                ids.isEmpty() -> actionTr("any_island", "Any Island (All)")
                ids.size <= 2 -> itemList.filter { it.id in ids }.joinToString(", ") { it.label }
                else -> actionTr("islands_selected", "%d Islands Selected", ids.size)
            }
        }
    }

    private val floorDropdown = Dropdown().apply {
        items = FLOOR_OPTIONS.map { Dropdown.Item(it) }
        onSelect = {
            if (isEntrance) inBoss = null
            if (!hasPhase) phase = null
            updateLabels()
        }
    }

    private val bossToggle = Button("") {
        inBoss = when (inBoss) {
            null -> true
            true -> false
            false -> null
        }
        updateLabels()
    }

    private val phaseToggle = Button("") {
        phase = when (val current = phase) {
            null -> 1
            in 1 until LAST_PHASE -> current + 1
            else -> null
        }
        updateLabels()
    }

    private val selectedFloor: DungeonFloor?
        get() = DungeonFloor.entries.getOrNull(floorDropdown.selectedIndex - 1)

    private val isCatacombs: Boolean get() = areaDropdown.selectedIds.any { it.equalsIgnoreCase(CATACOMBS) }
    private val isEntrance: Boolean get() = selectedFloor == DungeonFloor.ENTRANCE
    private val hasPhase: Boolean get() = selectedFloor?.isF7orM7 != false

    fun attachTo(host: WidgetHost) {
        // If it's not drawn, the floor picker takes no input.
        listOf(bossToggle, phaseToggle, areaDropdown, floorDropdown).forEach { host.add(it) }
    }

    fun readFrom(source: LocationFilterProvider) {
        areaDropdown.selectedIds.clear()
        areaDropdown.selectedIds.addAll(source.areaFilters)
        floorDropdown.selectedIndex = floorIndex(source.floorFilter)
        inBoss = source.inBossFilter
        phase = source.phaseFilter
        updateLabels()
    }

    fun readFromCurrentLocation() {
        areaDropdown.selectedIds.clear()
        LocationUtils.area?.let { area ->
            areaDropdown.selectedIds.add(SkyblockAreas.allNames.firstOrNull { it.equalsIgnoreCase(area) } ?: area)
        }
        val floor = LocationUtils.dungeonFloor
        floorDropdown.selectedIndex = floorIndex(floor)
        inBoss = if (floor == DungeonFloor.ENTRANCE) null else LocationUtils.inBoss.takeIf { LocationUtils.inDungeon }
        phase = LocationUtils.f7Phase?.number
        updateLabels()
    }

    /** Only keeps conditions the chosen island and floor can show. */
    fun writeTo(target: LocationFilterProvider) {
        val catacombs = isCatacombs
        target.areaFilters = areaDropdown.selectedIds.toMutableList()
        target.inDungeonFilter = if (catacombs) true else null
        target.floorFilter = if (catacombs) selectedFloor else null
        target.inBossFilter = if (catacombs && !isEntrance) inBoss else null
        target.phaseFilter = if (catacombs && hasPhase) phase else null
    }

    fun section(builder: LayoutBuilder, formW: Float, mouseX: Float, mouseY: Float) = with(builder) {
        text(actionTr("area_filter", "Island / Area Filter:"), TextStyle.SMALL_MUTED_MEDIUM)
        cell(formW, 22f) { cx, cy -> areaDropdown.render(cx, cy, formW, 22f, mouseX, mouseY) }
        if (!isCatacombs) return@with

        text(actionTr("floor_filter", "Dungeon Floor Filter:"), TextStyle.SMALL_MUTED_MEDIUM)
        cell(formW, 22f) { cx, cy -> floorDropdown.render(cx, cy, formW, 22f, mouseX, mouseY) }
        if (isEntrance) return@with

        text(actionTr("location_conditions", "Location Conditions:"), TextStyle.SMALL_MUTED_MEDIUM)
        if (hasPhase) {
            row(spacing = Theme.PADDING_SMALL, align = Align.CENTER, width = formW) {
                val colW = (formW - Theme.PADDING_SMALL) / 2f
                cell(colW, 22f) { cx, cy -> bossToggle.render(cx, cy, colW, 22f, mouseX, mouseY) }
                cell(colW, 22f) { cx, cy -> phaseToggle.render(cx, cy, colW, 22f, mouseX, mouseY) }
            }
        } else {
            cell(formW, 22f) { cx, cy -> bossToggle.render(cx, cy, formW, 22f, mouseX, mouseY) }
        }
    }

    fun renderOverlays(mouseX: Float, mouseY: Float) {
        if (isCatacombs) floorDropdown.renderOverlay(mouseX, mouseY)
        areaDropdown.renderOverlay(mouseX, mouseY)
    }

    private fun updateLabels() {
        val boss = when (inBoss) {
            null -> actionTr("any", "ANY")
            true -> actionTr("yes", "YES")
            false -> actionTr("no", "NO")
        }
        bossToggle.label = actionTr("boss_value", "Boss: %s", boss)
        phaseToggle.label = actionTr("phase_value", "Phase: %s", phase?.takeIf { it in 1..LAST_PHASE }?.let { "P$it" } ?: actionTr("any", "ANY"))
    }

    private fun floorIndex(floor: DungeonFloor?) = floor?.let { it.ordinal + 1 } ?: 0

    internal companion object {
        const val CATACOMBS = "Catacombs"
        const val LAST_PHASE = 5
        val FLOOR_OPTIONS = listOf(actionTr("any_floor", "Any Floor")) + DungeonFloor.entries.map { it.label }
    }
}

internal fun LocationFilterProvider.dropUnreachable() {
    val catacombs = areaFilters.any { it.equalsIgnoreCase(LocationFilterFields.CATACOMBS) }
    inDungeonFilter = if (catacombs) true else null
    if (!catacombs) floorFilter = null
    if (!catacombs || floorFilter == DungeonFloor.ENTRANCE) inBossFilter = null
    if (!catacombs || floorFilter?.isF7orM7 == false) phaseFilter = null
}
