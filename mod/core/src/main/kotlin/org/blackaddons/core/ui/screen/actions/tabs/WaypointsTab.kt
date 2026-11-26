package org.blackaddons.core.ui.screen.actions.tabs

import org.blackaddons.api.Platform
import org.blackaddons.api.input.Keys
import org.blackaddons.api.ui.layout.centered
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.WidgetHost
import org.blackaddons.api.util.formatCoordinates
import org.blackaddons.core.actions.ActionGroup
import org.blackaddons.core.actions.ActionProfile
import org.blackaddons.core.actions.ActionStorage
import org.blackaddons.core.actions.WaypointDefinition
import org.blackaddons.core.ui.screen.actions.*
import java.awt.Color
import java.util.Locale
import kotlin.math.sqrt

internal class WaypointsTab(widgets: WidgetHost) : GroupedTab<WaypointDefinition>(widgets, ActionsPage.WAYPOINT) {

    override val items: MutableList<WaypointDefinition> get() = ActionStorage.waypoints

    init {
        list.emptyMessage = actionTr("waypoint.empty", "No waypoint actions. Click + Add Action to create one.")
    }

    override fun content(group: ActionGroup, members: MutableList<WaypointDefinition>) =
        ActionProfile(waypointGroups = mutableListOf(group), waypoints = members)

    private fun swatchOf(wp: WaypointDefinition) = Color((wp.colorRgb and 0xFFFFFF) or 0xFF000000.toInt(), true)

    override fun drawItem(item: WaypointDefinition, index: Int, rx: Float, ry: Float, rw: Float) {
        val wp = item
        val rowH = list.rowHeight

        ActionRow.background(rx, ry, rw, rowH, wp.enabled)
        list.grip(index, rx + ActionRow.GRIP_X, ry)

        val swatchX = rx + ActionRow.TITLE_X
        Render.rect(swatchX, centered(ry, rowH, SWATCH), SWATCH, SWATCH, swatchOf(wp), 3f)

        val stripLeft = ActionRowButtons.stripLeft(rx, rw)
        val distance = distanceLabel(wp)
        val distanceW = if (distance.isEmpty()) 0f else Render.textWidth(distance, Theme.FONT_SMALL, Fonts.medium)
        if (distance.isNotEmpty()) {
            Render.text(
                distance, stripLeft - distanceW - 12f,
                Render.centerY(ry, rowH, TextStyle.SMALL_MUTED_MEDIUM),
                TextStyle.SMALL_MUTED_MEDIUM,
            )
        }

        val nameX = swatchX + SWATCH + 8f
        val name = wp.name.ifBlank { actionTr("waypoint.unnamed", "Waypoint (%s)", formatCoordinates(wp.x, wp.y, wp.z)) }
        val delete = {
            ActionStorage.waypoints.remove(wp)
            ActionStorage.save()
        }
        val steps = { ActionEditors.openSteps(ActionEditors.waypointSteps(wp)) }
        widgets.clickable(rx, ry, rw, rowH, Keys.MOUSE_RIGHT) {
            ActionMenu.row(
                name, ActionProfile(waypoints = mutableListOf(wp)),
                edit = { ActionEditors.editWaypoint(wp) }, steps, delete,
            )
        }
        ActionRow.title(
            name, nameX, ry, rowH,
            maxWidth = stripLeft - (if (distance.isEmpty()) 12f else distanceW + 24f) - nameX,
            enabled = wp.enabled,
        )

        ActionRowButtons.draw(widgets, rx, ry, rw, wp.enabled) { button ->
            when (button) {
                RowButton.DELETE -> confirmDelete(rx, ry, rw, delete = delete)
                RowButton.STEPS -> steps()
                RowButton.EDIT -> ActionEditors.editWaypoint(wp)
                RowButton.TOGGLE -> {
                    wp.enabled = !wp.enabled
                    ActionStorage.save()
                }
            }
        }
    }

    private fun distanceLabel(wp: WaypointDefinition): String {
        if (!Platform.player.isInGame) return ""
        val pos = Platform.player.pos
        val dx = pos.x - wp.x
        val dy = pos.y - wp.y
        val dz = pos.z - wp.z
        return "%.1fm".format(Locale.ROOT, sqrt(dx * dx + dy * dy + dz * dz))
    }

    override fun onAdd() {
        ActionEditors.createWaypoint()
    }

    private companion object {
        const val SWATCH = 12f
    }
}
