package org.blackaddons.core.ui.screen.actions.tabs

import org.blackaddons.api.input.Keys
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.WidgetHost
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.formatDecimals
import org.blackaddons.api.util.ticksToSeconds
import org.blackaddons.core.actions.ActionGroup
import org.blackaddons.core.actions.ActionProfile
import org.blackaddons.core.actions.ActionStorage
import org.blackaddons.core.actions.LocationAction
import org.blackaddons.core.ui.screen.actions.*
import java.awt.Color

internal class LocationsTab(widgets: WidgetHost) : GroupedTab<LocationAction>(widgets, ActionsPage.LOCATION) {

    override val items: MutableList<LocationAction> get() = ActionStorage.locationActions

    init {
        list.emptyMessage = actionTr("location.empty", "No location actions. Click + Add Action to create an area transition trigger.")
    }

    override fun content(group: ActionGroup, members: MutableList<LocationAction>) =
        ActionProfile(locationGroups = mutableListOf(group), locationActions = members)

    override fun drawItem(item: LocationAction, index: Int, rx: Float, ry: Float, rw: Float) {
        val action = item
        val rowH = list.rowHeight
        ActionRow.background(rx, ry, rw, rowH, action.enabled)

        val name = action.name.ifBlank { actionTr("location.fallback", "Location Trigger") }
        val pillLimit = ActionRowButtons.stripLeft(rx, rw) - 10f
        list.grip(index, rx + ActionRow.GRIP_X, ry)
        var x = ActionRow.title(name, rx + ActionRow.TITLE_X, ry, rowH, TITLE_W, action.enabled, Fonts.bold)
        val delete = {
            ActionStorage.locationActions.remove(action)
            ActionStorage.save()
        }
        widgets.clickable(rx, ry, rw, rowH, Keys.MOUSE_RIGHT) {
            ActionMenu.row(
                name, ActionProfile(locationActions = mutableListOf(action)),
                edit = { ActionEditors.editLocation(action) }, steps = { ActionEditors.openSteps(action.actions) }, delete,
            )
        }

        x = ActionRow.pill(areaLabel(action), x, ry, rowH, AREA_PILL, pillLimit)
        x = ActionRow.pill(action.inDungeonFilter?.let { actionTr(if (it) "dungeon" else "no_dungeon", if (it) "Dungeon" else "No Dungeon") }, x, ry, rowH, DUNGEON_PILL, pillLimit)
        x = ActionRow.pill(action.floorFilter?.label, x, ry, rowH, Theme.ACCENT.withAlpha(140), pillLimit)
        x = ActionRow.pill(action.inBossFilter?.let { actionTr(if (it) "boss" else "no_boss", if (it) "Boss" else "No Boss") }, x, ry, rowH, BOSS_PILL, pillLimit)
        x = ActionRow.pill(action.phaseFilter?.let { "P$it" }, x, ry, rowH, PHASE_PILL, pillLimit)
        x = ActionRow.pill(action.triggerEvent?.translatedName, x, ry, rowH, Theme.ACCENT.withAlpha(140), pillLimit)
        if (action.delayServerTicks > 0) {
            x = ActionRow.pill(actionTr("tick_delay_value", "%st (%ss) delay", action.delayServerTicks, action.delayServerTicks.ticksToSeconds.formatDecimals()), x, ry, rowH, Theme.SURFACE_ACTIVE, pillLimit)
        } else if (action.delaySeconds > 0f) {
            x = ActionRow.pill(actionTr("delay_value", "%ss delay", action.delaySeconds.formatDecimals(1)), x, ry, rowH, Theme.SURFACE_ACTIVE, pillLimit)
        }
        x = ActionRow.pill(actionTr("cooldown_value", "%ss cooldown", action.cooldownSeconds.formatDecimals(1)), x, ry, rowH, Theme.SURFACE_ACTIVE, pillLimit)
        ActionRow.pill(I18n.steps(action.actions.size), x, ry, rowH, Theme.SURFACE_ACTIVE, pillLimit)

        ActionRowButtons.draw(widgets, rx, ry, rw, action.enabled) { button ->
            when (button) {
                RowButton.DELETE -> confirmDelete(rx, ry, rw, delete = delete)
                RowButton.STEPS -> ActionEditors.openSteps(action.actions)
                RowButton.EDIT -> ActionEditors.editLocation(action)
                RowButton.TOGGLE -> {
                    action.enabled = !action.enabled
                    ActionStorage.save()
                }
            }
        }
    }

    private fun areaLabel(action: LocationAction): String? = when (action.areaFilters.size) {
        0 -> null
        1 -> action.areaFilters[0]
        else -> actionTr("areas", "%d Areas", action.areaFilters.size)
    }

    override fun onAdd() {
        ActionEditors.createLocation()
    }

    private companion object {
        const val TITLE_W = 140f
        val AREA_PILL = Color(120, 60, 160, 180)
        val DUNGEON_PILL = Color(0, 100, 40, 180)
        val BOSS_PILL = Color(160, 40, 40, 180)
        val PHASE_PILL = Color(40, 80, 160, 180)
    }
}
