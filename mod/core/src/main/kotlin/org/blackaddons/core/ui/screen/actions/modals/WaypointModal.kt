package org.blackaddons.core.ui.screen.actions.modals

import org.blackaddons.api.Platform
import org.blackaddons.api.ui.layout.Align
import org.blackaddons.api.ui.layout.LayoutBuilder
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.screen.color.ColorPopover
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.Slider
import org.blackaddons.api.ui.widget.dropdown.Dropdown
import org.blackaddons.api.ui.widget.dropdown.MultiSelectDropdown
import org.blackaddons.api.ui.widget.text.TextInput
import org.blackaddons.api.util.formatDecimals
import org.blackaddons.core.actions.*
import org.blackaddons.core.ui.screen.actions.actionTr
import org.blackaddons.core.ui.screen.actions.fields.GroupPicker
import org.blackaddons.core.ui.screen.actions.fields.labelledSlider
import org.blackaddons.core.ui.screen.actions.translatedName
import java.awt.Color
import kotlin.math.floor

/** Waypoint editor. Feedback is on its first [WaypointAction], so [commit] creates one if needed. */
internal class WaypointModal(
    private val waypoint: WaypointDefinition,
    onSaved: () -> Unit = {},
) : ActionEditModalBase(onSaved) {

    override val modalTitle: String
        get() = actionTr("waypoint.edit", "Edit Waypoint (%s)", nameInput.text.trim().ifEmpty { actionTr("unnamed", "Unnamed") })

    override val formHeight: Float get() = 540f

    private var shape = waypoint.shape
    private var showBeacon = waypoint.showBeacon
    private var showFullShape = waypoint.showFullShape
    private var showShape = waypoint.showShape
    private var showText = waypoint.showText
    private var colorRgb = waypoint.colorRgb

    private val trigger = waypoint.actions.firstOrNull()

    private val nameInput = TextInput(placeholder = actionTr("waypoint.name_placeholder", "Waypoint Name")).apply { text = waypoint.name }
    private val group = GroupPicker(widgets, ActionStorage.waypointGroups, waypoint.groupId)

    private val triggersDropdown = widgets.add(MultiSelectDropdown()).apply {
        items = listOf(
            MultiSelectDropdown.Item(ENTRY, actionTr("waypoint.trigger.entry", "Entry")),
            MultiSelectDropdown.Item(EXIT, actionTr("waypoint.trigger.exit", "Exit")),
            MultiSelectDropdown.Item(GUI_EXIT, actionTr("waypoint.trigger.gui_exit", "GUI Exit")),
        )
        selectedFormatter = { ids, itemList ->
            val chosen = itemList.filter { it.id in ids }.joinToString(", ") { it.label }
            actionTr("waypoint.triggers", "Triggers: %s", chosen.ifEmpty { actionTr("waypoint.triggers_none", "None") })
        }
        if (trigger?.triggerOnEntry ?: true) selectedIds += ENTRY
        if (trigger?.triggerOnExit == true) selectedIds += EXIT
        if (trigger?.triggerOnGuiExit == true) selectedIds += GUI_EXIT
    }

    private val triggeredByDropdown = widgets.add(Dropdown()).apply {
        items = TriggeredBy.entries.map { Dropdown.Item(actionTr("waypoint.triggered_by", "Triggered by: %s", it.translatedName)) }
        selectedIndex = (trigger?.triggeredBy ?: TriggeredBy.ME).ordinal
    }

    private val xInput = TextInput(placeholder = "X").apply { text = waypoint.x.formatDecimals(2) }
    private val yInput = TextInput(placeholder = "Y").apply { text = waypoint.y.formatDecimals(2) }
    private val zInput = TextInput(placeholder = "Z").apply { text = waypoint.z.formatDecimals(2) }

    private val radiusSlider = Slider(min = 0.01, max = 10.0, value = waypoint.radius, step = 0.01)
    private val heightSlider = Slider(min = 0.01, max = 2.0, value = waypoint.height, step = 0.01)
    private val cooldownSlider = Slider(min = 0.0, max = 60.0, value = waypoint.reuseCooldownSeconds.toDouble(), step = 0.1)

    private val usePlayerPosBtn = Button(actionTr("waypoint.use_current", "Use Current Position")) { setCoords(Platform.player.pos.x, Platform.player.pos.y, Platform.player.pos.z) }

    // Uses the standing position as backup, so the button always does something.
    private val usePlayerLookBtn = Button(actionTr("waypoint.use_looking", "Use Looked-at Position")) {
        val target = Platform.interaction.crosshairBlock
        if (target != null) setCoords(target.x + 0.5, target.y.toDouble(), target.z + 0.5)
        else setCoords(Platform.player.pos.x, Platform.player.pos.y, Platform.player.pos.z)
    }

    private val roundPosBtn = Button(actionTr("waypoint.round", "Round to .5")) {
        xInput.text.toDoubleOrNull()?.let { xInput.text = (floor(it) + 0.5).formatDecimals(2) }
        zInput.text.toDoubleOrNull()?.let { zInput.text = (floor(it) + 0.5).formatDecimals(2) }
    }

    private val colorPickerBtn = Button(actionTr("waypoint.pick_color", "Pick Color")) {
        val opaque = if ((colorRgb and 0xFF000000.toInt()) == 0) colorRgb or 0xFF000000.toInt() else colorRgb
        UiManager.push(
            ColorPopover(
                initial = Color(opaque, true),
                allowAlpha = false,
                onChange = { c -> colorRgb = c.rgb and 0xFFFFFF },
            ),
        )
    }

    private val beaconToggle = Button("") { showBeacon = !showBeacon; updateLabels() }
    private val filledToggle = Button("") { showFullShape = !showFullShape; updateLabels() }
    private val shapeOutlineToggle = Button("") { showShape = !showShape; updateLabels() }
    private val textToggle = Button("") { showText = !showText; updateLabels() }
    private val shapeToggle = Button("") {
        shape = if (shape == WaypointShape.CYLINDER) WaypointShape.BOX else WaypointShape.CYLINDER
        updateLabels()
    }

    init {
        listOf(
            nameInput, xInput, yInput, zInput, radiusSlider, heightSlider, cooldownSlider,
            usePlayerPosBtn, usePlayerLookBtn, roundPosBtn, colorPickerBtn,
            beaconToggle, filledToggle, shapeOutlineToggle, textToggle, shapeToggle,
        ).forEach { widgets.add(it) }

        workingSteps.addAll(trigger?.actions.orEmpty())
        feedback.readFrom(trigger)
        updateLabels()
        ready()
    }

    private fun setCoords(x: Double, y: Double, z: Double) {
        xInput.text = x.formatDecimals(2)
        yInput.text = y.formatDecimals(2)
        zInput.text = z.formatDecimals(2)
    }

    private fun updateLabels() {
        shapeToggle.label = actionTr("waypoint.shape_value", "Shape: %s", actionTr("waypoint.shape.${shape.name.lowercase()}", shape.name))
        beaconToggle.label = actionTr("waypoint.beacon", "Beacon: %s", actionTr(if (showBeacon) "on" else "off", if (showBeacon) "ON" else "OFF"))
        filledToggle.label = actionTr("waypoint.filled", "Filled: %s", actionTr(if (showFullShape) "on" else "off", if (showFullShape) "ON" else "OFF"))
        shapeOutlineToggle.label = actionTr("waypoint.outline", "Outline: %s", actionTr(if (showShape) "on" else "off", if (showShape) "ON" else "OFF"))
        textToggle.label = actionTr("waypoint.text", "Text: %s", actionTr(if (showText) "on" else "off", if (showText) "ON" else "OFF"))
    }

    override fun formSection(builder: LayoutBuilder, formW: Float, mouseX: Float, mouseY: Float) = with(builder) {
        text(actionTr("name", "Name:"), TextStyle.SMALL_MUTED_MEDIUM)
        cell(formW, 22f) { cx, cy -> nameInput.render(cx, cy, formW, 22f, mouseX, mouseY) }
        group.section(this, formW, mouseX, mouseY)

        text(actionTr("coordinates", "Coordinates (X, Y, Z):"), TextStyle.SMALL_MUTED_MEDIUM)
        thirds(formW) { colW ->
            cell(colW, 22f) { cx, cy -> xInput.render(cx, cy, colW, 22f, mouseX, mouseY) }
            cell(colW, 22f) { cx, cy -> yInput.render(cx, cy, colW, 22f, mouseX, mouseY) }
            cell(colW, 22f) { cx, cy -> zInput.render(cx, cy, colW, 22f, mouseX, mouseY) }
        }
        thirds(formW) { colW ->
            cell(colW, 22f) { cx, cy -> usePlayerPosBtn.render(cx, cy, colW, 22f, mouseX, mouseY) }
            cell(colW, 22f) { cx, cy -> usePlayerLookBtn.render(cx, cy, colW, 22f, mouseX, mouseY) }
            cell(colW, 22f) { cx, cy -> roundPosBtn.render(cx, cy, colW, 22f, mouseX, mouseY) }
        }
        thirds(formW) { colW ->
            labelledSlider(actionTr("radius", "Radius"), radiusSlider.value.formatDecimals(2), colW) { cx, cy ->
                radiusSlider.render(cx, cy, colW, 16f, mouseX, mouseY)
            }
            labelledSlider(actionTr("height", "Height"), heightSlider.value.formatDecimals(2), colW) { cx, cy ->
                heightSlider.render(cx, cy, colW, 16f, mouseX, mouseY)
            }
            labelledSlider(actionTr("cooldown_label", "Cooldown"), "${cooldownSlider.value.formatDecimals(1)}s", colW) { cx, cy ->
                cooldownSlider.render(cx, cy, colW, 16f, mouseX, mouseY)
            }
        }

        row(spacing = Theme.PADDING_SMALL, align = Align.CENTER, width = formW) {
            val colW = (formW - Theme.PADDING_SMALL * 4f - SWATCH_W) / 4f
            cell(SWATCH_W, 22f) { cx, cy ->
                val swatch = Color((colorRgb and 0xFFFFFF) or 0xFF000000.toInt(), true)
                Render.rect(cx, cy, SWATCH_W, 22f, swatch, Theme.RADIUS_SMALL)
                Render.hollowRect(cx, cy, SWATCH_W, 22f, 1f, Theme.BORDER, Theme.RADIUS_SMALL)
            }
            cell(colW, 22f) { cx, cy -> colorPickerBtn.render(cx, cy, colW, 22f, mouseX, mouseY) }
            cell(colW, 22f) { cx, cy -> shapeToggle.render(cx, cy, colW, 22f, mouseX, mouseY) }
            cell(colW, 22f) { cx, cy -> shapeOutlineToggle.render(cx, cy, colW, 22f, mouseX, mouseY) }
            cell(colW, 22f) { cx, cy -> textToggle.render(cx, cy, colW, 22f, mouseX, mouseY) }
        }

        row(spacing = Theme.PADDING_SMALL, align = Align.CENTER, width = formW) {
            val colW = (formW - Theme.PADDING_SMALL) / 2f
            cell(colW, 22f) { cx, cy -> beaconToggle.render(cx, cy, colW, 22f, mouseX, mouseY) }
            cell(colW, 22f) { cx, cy -> filledToggle.render(cx, cy, colW, 22f, mouseX, mouseY) }
        }
        row(spacing = Theme.PADDING_SMALL, align = Align.CENTER, width = formW) {
            val colW = (formW - Theme.PADDING_SMALL) / 2f
            cell(colW, 22f) { cx, cy -> triggersDropdown.render(cx, cy, colW, 22f, mouseX, mouseY) }
            cell(colW, 22f) { cx, cy -> triggeredByDropdown.render(cx, cy, colW, 22f, mouseX, mouseY) }
        }
    }

    private fun LayoutBuilder.thirds(formW: Float, block: LayoutBuilder.(Float) -> Unit) {
        row(spacing = Theme.PADDING_SMALL, align = Align.CENTER, width = formW) {
            block((formW - Theme.PADDING_SMALL * 2f) / 3f)
        }
    }

    override fun renderOverlays(mouseX: Float, mouseY: Float) {
        group.renderOverlay(mouseX, mouseY)
        triggersDropdown.renderOverlay(mouseX, mouseY)
        triggeredByDropdown.renderOverlay(mouseX, mouseY)
    }

    override fun commit() {
        waypoint.name = nameInput.text.trim()
        waypoint.groupId = group.selected
        waypoint.x = xInput.text.toDoubleOrNull() ?: 0.0
        waypoint.y = yInput.text.toDoubleOrNull() ?: 0.0
        waypoint.z = zInput.text.toDoubleOrNull() ?: 0.0
        waypoint.radius = radiusSlider.value
        waypoint.height = heightSlider.value
        waypoint.reuseCooldownSeconds = cooldownSlider.value.toFloat()
        waypoint.shape = shape
        waypoint.showBeacon = showBeacon
        waypoint.showFullShape = showFullShape
        waypoint.showShape = showShape
        waypoint.showText = showText
        waypoint.colorRgb = colorRgb

        if (waypoint.actions.isEmpty()) waypoint.actions.add(WaypointAction())
        waypoint.actions.first().let { action ->
            action.triggerOnEntry = ENTRY in triggersDropdown.selectedIds
            action.triggerOnExit = EXIT in triggersDropdown.selectedIds
            action.triggerOnGuiExit = GUI_EXIT in triggersDropdown.selectedIds
            action.triggeredBy = TriggeredBy.entries[triggeredByDropdown.selectedIndex]
            feedback.writeTo(action)
            action.actions.clear()
            action.actions.addAll(workingSteps)
        }
        ActionStorage.save()
    }

    override fun remove() {
        ActionStorage.waypoints.remove(waypoint)
        ActionStorage.save()
    }

    private companion object {
        const val SWATCH_W = 24f

        const val ENTRY = "entry"
        const val EXIT = "exit"
        const val GUI_EXIT = "gui_exit"
    }
}
