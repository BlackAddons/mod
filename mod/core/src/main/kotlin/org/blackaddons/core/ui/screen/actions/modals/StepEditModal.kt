package org.blackaddons.core.ui.screen.actions.modals

import org.blackaddons.api.ui.layout.Align
import org.blackaddons.api.ui.layout.column
import org.blackaddons.api.ui.layout.row
import org.blackaddons.api.ui.node.NodePropertyForm
import org.blackaddons.api.ui.screen.ModalPanel
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.Scroll
import org.blackaddons.api.ui.widget.dropdown.Dropdown
import org.blackaddons.core.actions.ActionStep
import org.blackaddons.core.actions.execution.ActionExecutor
import org.blackaddons.core.actions.steps.ElseStep
import org.blackaddons.core.actions.steps.EndStep
import org.blackaddons.core.ui.screen.actions.actionTr
import org.blackaddons.core.ui.screen.actions.graph.ActionGraphProperties
import org.blackaddons.core.ui.screen.actions.typeName

/**
 * Edits a copy of [step] with the rows its type declares, the same rows the graph shows. Save and Escape write the copy
 * back into [step], Cancel drops it. [inGraph] leaves out Else and End, because the graph wires already imply them.
 */
internal class StepEditModal(
    private val step: ActionStep,
    private val inGraph: Boolean = false,
    private val onSave: () -> Unit = {},
) : ModalPanel(actionTr("step.edit", "Edit Action Step")) {

    override val panelWidth: Float
        get() = widthShare(0.75f, min = 480f)

    override val panelHeight: Float
        get() = heightShare(0.82f, min = 480f, margin = 30f)

    private val draft = step.copy()
    private val types = ActionExecutor.availableTypes().filter { !inGraph || (it !== EndStep && it !== ElseStep) }

    // A step whose addon is missing keeps its type as the first entry, so it can still be saved unchanged.
    private val missing = draft.type == null

    private val scroll = Scroll()
    private val form = NodePropertyForm({ ActionGraphProperties.editorRows(draft) }, ::rebuildHost)

    private val typeDropdown = Dropdown().apply {
        items = (if (missing) listOf(draft.typeName) else emptyList()).plus(types.map { it.displayName }).map { Dropdown.Item(it) }
        selectedIndex = if (missing) 0 else types.indexOf(draft.type).coerceAtLeast(0)
        onSelect = { index ->
            val type = types.getOrNull(if (missing) index - 1 else index)
            if (type != null && type !== draft.type) {
                draft.retype(type)
                form.invalidate()
            }
        }
    }

    private val sequentialToggle = Button("") {
        draft.waitForPrevious = !draft.waitForPrevious
        updateToggleLabels()
    }

    private val enabledToggle = Button("") {
        draft.enabled = !draft.enabled
        updateToggleLabels()
    }

    private val saveButton = Button(actionTr("step.save", "Save Step")) {
        save()
        UiManager.pop()
    }.apply { accent = true }

    private val cancelButton = Button(actionTr("cancel", "Cancel")) {
        UiManager.pop()
    }

    init {
        updateToggleLabels()
        rebuildHost()
    }

    private fun updateToggleLabels() {
        enabledToggle.label = toggleLabel("step.enabled", "Step", draft.enabled)
        sequentialToggle.label = toggleLabel("step.wait_previous", "Wait for Previous", draft.waitForPrevious)
    }

    private fun toggleLabel(key: String, fallback: String, enabled: Boolean): String =
        actionTr(key, "$fallback: %s", actionTr(if (enabled) "on" else "off", if (enabled) "ON" else "OFF"))

    private fun rebuildHost() {
        val prevFocused = widgets.focusedTarget
        widgets.clear()
        widgets.add(scroll)
        widgets.add(cancelButton)
        widgets.add(saveButton)
        widgets.add(typeDropdown)
        widgets.add(sequentialToggle)
        widgets.add(enabledToggle)
        form.attachTo(widgets)
        if (prevFocused != null) widgets.focus(prevFocused)
    }

    override fun onEscape() = save()

    private fun save() {
        step.assign(draft)
        onSave()
    }

    override fun renderContent(mouseX: Float, mouseY: Float) {
        val fixedBottomH = 32f
        val scrollableH = contentH - fixedBottomH - 6f
        val formW = if (scroll.maxScroll > 0f) contentW - 10f else contentW

        val layout = column(spacing = Theme.PADDING_SMALL, width = formW) {
            text(actionTr("step.type_execution", "Step Type & Execution:"), TextStyle.SMALL_MUTED_MEDIUM)
            row(spacing = Theme.PADDING_SMALL, align = Align.CENTER, width = formW) {
                val seqW = 125f
                val enabledW = 85f
                val dropW = formW - seqW - enabledW - Theme.PADDING_SMALL * 2f
                cell(dropW, 22f) { cx, cy -> typeDropdown.render(cx, cy, dropW, 22f, mouseX, mouseY) }
                cell(seqW, 22f) { cx, cy -> sequentialToggle.render(cx, cy, seqW, 22f, mouseX, mouseY) }
                cell(enabledW, 22f) { cx, cy -> enabledToggle.render(cx, cy, enabledW, 22f, mouseX, mouseY) }
            }
            if (missing && draft.type == null) {
                text(actionTr("step.missing_help", "The addon with this step is not installed. The step keeps its settings and is skipped."), TextStyle.SMALL_MUTED)
            }
            form.section(this, formW, mouseX, mouseY)
        }

        scroll.render(contentX, contentY, contentW, scrollableH, layout.height) { offset ->
            layout.draw(contentX, contentY - offset)
        }

        val footerY = contentY + contentH - 24f
        val footer = row(spacing = Theme.PADDING, align = Align.CENTER, width = contentW) {
            flexSpacer()
            cell(80f, 24f) { cx, cy -> cancelButton.render(cx, cy, 80f, 24f, mouseX, mouseY) }
            cell(90f, 24f) { cx, cy -> saveButton.render(cx, cy, 90f, 24f, mouseX, mouseY) }
        }
        footer.draw(contentX, footerY)

        form.renderOverlays(mouseX, mouseY)
        typeDropdown.renderOverlay(mouseX, mouseY)
    }
}
