package org.blackaddons.core.ui.screen.actions.modals
import org.blackaddons.api.ui.layout.Align
import org.blackaddons.api.ui.layout.column
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.ModalPanel
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.toast.ToastKind
import org.blackaddons.api.ui.toast.Toasts
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.Slider
import org.blackaddons.api.ui.widget.dropdown.Dropdown
import org.blackaddons.api.ui.widget.text.TextInput
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.formatDecimals
import org.blackaddons.api.util.ticksToSeconds
import org.blackaddons.core.actions.ActionDungeonEvent
import org.blackaddons.core.actions.ActionStep
import org.blackaddons.core.actions.ActionStorage
import org.blackaddons.core.actions.LocationAction
import org.blackaddons.core.actions.steps.SendMessageStep
import org.blackaddons.core.ui.screen.actions.actionTr
import org.blackaddons.core.ui.screen.actions.fields.GroupPicker
import org.blackaddons.core.ui.screen.actions.fields.LocationFilterFields
import org.blackaddons.core.ui.screen.actions.translatedName

internal class LocationActionModal(
    private val action: LocationAction,
    private val isNew: Boolean = false,
    private val onSaved: () -> Unit = {},
) : ModalPanel(actionTr(if (isNew) "location.new" else "location.edit", if (isNew) "New Location Action" else "Edit Location Action")) {

    override val panelWidth: Float
        get() = fitWidth(440f, min = 320f)

    override val panelHeight: Float
        get() = fitHeight(572f, min = 340f, margin = 30f)

    private val nameInput = widgets.add(TextInput(placeholder = actionTr("location.name_placeholder", "Trigger Name (e.g. Enter F7)")).apply {
        text = action.name
    })

    private val group = GroupPicker(widgets, ActionStorage.locationGroups, action.groupId)

    private val location = LocationFilterFields().apply {
        attachTo(widgets)
        readFrom(action)
    }

    // The first item fires on entry. The rest fire on that dungeon event while inside.
    private val triggerDropdown = widgets.add(Dropdown().apply {
        items = listOf(Dropdown.Item(actionTr("location.on_enter", "On entering the location"))) +
            ActionDungeonEvent.entries.map { Dropdown.Item(actionTr("location.on_event", "On %s", it.translatedName)) }
        selectedIndex = action.triggerEvent?.let { it.ordinal + 1 } ?: 0
    })

    private val delaySlider = widgets.add(Slider(min = 0.0, max = 60.0, value = action.delaySeconds.toDouble(), step = 0.1))
    private val cooldownSlider = widgets.add(Slider(min = 0.5, max = 60.0, value = action.cooldownSeconds.toDouble(), step = 0.5))
    private val tickDelayInput = widgets.add(TextInput(placeholder = "0", maxLength = 9, charFilter = Char::isDigit).apply {
        text = action.delayServerTicks.takeIf { it > 0 }?.toString() ?: ""
    })
    private val tickDelay: Int get() = tickDelayInput.text.toIntOrNull() ?: 0

    private val quickCommandInput = widgets.add(TextInput(placeholder = actionTr("location.quick_command_placeholder", "Quick Command (e.g. /party chat Ready)")).apply {
        text = action.actions.firstNotNullOfOrNull { it.paramsOf(SendMessageStep) }?.message ?: ""
    })

    private val stepsButton = widgets.add(Button("") {
        syncQuickCommand()
        UiManager.push(ActionStepsModal(action.actions) {
            updateStepsLabel()
        })
    })

    private val saveButton = widgets.add(Button(actionTr("save", "Save")) {
        save()
        UiManager.pop()
    }).apply { accent = true }

    init {
        updateStepsLabel()
    }

    private fun updateStepsLabel() {
        stepsButton.label = actionTr("configure_steps", "Configure Action Steps (%s)", I18n.steps(action.actions.size))
    }

    private fun syncQuickCommand() {
        val cmd = quickCommandInput.text.trim()
        val existing = action.actions.firstOrNull { it.type === SendMessageStep }
        if (cmd.isNotEmpty()) {
            val normalizedCmd = if (cmd.startsWith("/")) cmd else "/$cmd"
            if (existing != null) {
                existing.paramsOf(SendMessageStep)?.message = normalizedCmd
            } else {
                action.actions.add(0, ActionStep.of(SendMessageStep, SendMessageStep.Params(normalizedCmd)))
            }
        } else if (existing != null) {
            action.actions.remove(existing)
        }
    }

    private fun save() {
        action.name = nameInput.text.trim().ifEmpty { actionTr("location.fallback", "Location Trigger") }
        location.writeTo(action)
        action.triggerEvent = ActionDungeonEvent.entries.getOrNull(triggerDropdown.selectedIndex - 1)
        action.delaySeconds = delaySlider.value.toFloat()
        action.cooldownSeconds = cooldownSlider.value.toFloat()
        action.delayServerTicks = tickDelay
        action.groupId = group.selected

        syncQuickCommand()
        val duplicate = duplicateFiltersOf(action)

        if (isNew && !ActionStorage.locationActions.contains(action)) {
            ActionStorage.locationActions.add(action)
        }
        ActionStorage.save()
        duplicate?.let(::warnDuplicateFilters)
        onSaved()
    }

    override fun renderContent(mouseX: Float, mouseY: Float) {
        val form = column(spacing = Theme.PADDING_SMALL, width = contentW) {
            text(actionTr("location.name", "Trigger Name:"), TextStyle.SMALL_MUTED_MEDIUM)
            cell(contentW, 22f) { cx, cy -> nameInput.render(cx, cy, contentW, 22f, mouseX, mouseY) }
            group.section(this, contentW, mouseX, mouseY)

            location.section(this, contentW, mouseX, mouseY)

            text(actionTr("location.trigger", "Trigger:"), TextStyle.SMALL_MUTED_MEDIUM)
            cell(contentW, 22f) { cx, cy -> triggerDropdown.render(cx, cy, contentW, 22f, mouseX, mouseY) }

            row(spacing = Theme.PADDING_SMALL, align = Align.CENTER, width = contentW) {
                val colW = (contentW - Theme.PADDING_SMALL) / 2f
                cell(colW, 38f) { cx, cy ->
                    Render.text(actionTr("delay", "Delay: %ss", delaySlider.value.formatDecimals(1)), cx, Render.centerY(cy, 14f, TextStyle.SMALL_MUTED_MEDIUM), TextStyle.SMALL_MUTED_MEDIUM)
                    delaySlider.render(cx, cy + 16f, colW, 22f, mouseX, mouseY)
                }
                cell(colW, 38f) { cx, cy ->
                    Render.text(actionTr("cooldown", "Cooldown: %ss", cooldownSlider.value.formatDecimals(1)), cx, Render.centerY(cy, 14f, TextStyle.SMALL_MUTED_MEDIUM), TextStyle.SMALL_MUTED_MEDIUM)
                    cooldownSlider.render(cx, cy + 16f, colW, 22f, mouseX, mouseY)
                }
            }
            cell(contentW, 38f) { cx, cy ->
                Render.text(actionTr("tick_delay", "Server Tick Delay: %st (%ss)", tickDelay, tickDelay.ticksToSeconds.formatDecimals()), cx, Render.centerY(cy, 14f, TextStyle.SMALL_MUTED_MEDIUM), TextStyle.SMALL_MUTED_MEDIUM)
                tickDelayInput.render(cx, cy + 16f, contentW, 22f, mouseX, mouseY)
            }

            text(actionTr("quick_command", "Quick Command:"), TextStyle.SMALL_MUTED_MEDIUM)
            cell(contentW, 22f) { cx, cy -> quickCommandInput.render(cx, cy, contentW, 22f, mouseX, mouseY) }

            cell(contentW, 24f) { cx, cy -> stepsButton.render(cx, cy, contentW, 24f, mouseX, mouseY) }
        }

        form.draw(contentX, contentY)
        location.renderOverlays(mouseX, mouseY)
        triggerDropdown.renderOverlay(mouseX, mouseY)
        group.renderOverlay(mouseX, mouseY)

        val saveY = contentY + contentH - 26f
        saveButton.render(contentX, saveY, contentW, 26f, mouseX, mouseY)
    }

    override fun onEscape() {
        save()
    }
}

internal fun duplicateFiltersOf(action: LocationAction): LocationAction? = ActionStorage.locationActions.firstOrNull {
    it.id != action.id &&
        it.areaFilters.toSet() == action.areaFilters.toSet() &&
        it.inDungeonFilter == action.inDungeonFilter &&
        it.floorFilter == action.floorFilter &&
        it.inBossFilter == action.inBossFilter &&
        it.phaseFilter == action.phaseFilter &&
        it.triggerEvent == action.triggerEvent
}

internal fun warnDuplicateFilters(duplicate: LocationAction) {
    Toasts.show(
        actionTr("location.duplicate_title", "Duplicate location trigger"),
        actionTr("location.duplicate_message", "%s uses the same filters.", duplicate.name.ifBlank { actionTr("location.another", "Another trigger") }),
        ToastKind.ERROR,
    )
}
