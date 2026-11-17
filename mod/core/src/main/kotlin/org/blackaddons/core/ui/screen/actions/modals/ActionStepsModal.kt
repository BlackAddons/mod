package org.blackaddons.core.ui.screen.actions.modals
import org.blackaddons.api.ui.layout.Align
import org.blackaddons.api.ui.layout.column
import org.blackaddons.api.ui.layout.hovered
import org.blackaddons.api.ui.layout.row
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.ModalPanel
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.ScrollableList
import org.blackaddons.api.ui.widget.moveItem
import org.blackaddons.core.actions.ActionStep
import org.blackaddons.core.actions.ActionStorage
import org.blackaddons.core.actions.execution.ActionBlocks
import org.blackaddons.core.actions.steps.ElseStep
import org.blackaddons.core.ui.screen.actions.ActionEditors
import org.blackaddons.core.ui.screen.actions.actionTr
import org.blackaddons.core.ui.screen.actions.summary
import org.blackaddons.core.ui.screen.actions.typeName

private const val BLOCK_INDENT = 16f

internal class ActionStepsModal(
    private val steps: MutableList<ActionStep>,
    private val onDone: () -> Unit = {},
    private val persist: () -> Unit = ActionStorage::save,
) : ModalPanel(actionTr("steps.title", "Action Steps")) {

    override val panelWidth: Float
        get() = widthShare(0.75f, min = 480f)

    override val panelHeight: Float
        get() = heightShare(0.8f, min = 380f)

    // Indent depths for this frame. -1 is an End that closes nothing.
    private var depths = IntArray(0)

    private val addStepButton = widgets.add(Button(actionTr("steps.add", "+ Add Step")) {
        ActionEditors.createStep(steps, persist)
    }.apply { accent = true })

    private val doneButton = widgets.add(Button(actionTr("done", "Done")) {
        persist()
        UiManager.pop()
        onDone()
    }.apply { accent = true })

    override fun onEscape() {
        persist()
        onDone()
    }

    private val stepsList = widgets.add(ScrollableList<ActionStep>().apply {
        rowHeight = Theme.FEATURE_ROW_HEIGHT
        emptyMessage = actionTr("steps.empty", "No action steps added. Click + Add Step.")
        drawRow = { step, i, rx, ry, rw -> renderStepRow(step, i, rx, ry, rw) }
        onReorder = { from, to ->
            steps.moveItem(from, to)
            persist()
        }
    })

    override fun renderContent(mouseX: Float, mouseY: Float) {
        val header = row(spacing = Theme.PADDING, align = Align.CENTER, width = contentW) {
            flexSpacer()
            cell(100f, 24f) { cx, cy -> addStepButton.render(cx, cy, 100f, 24f, mouseX, mouseY) }
        }

        val listH = contentH - header.height - 35f
        val layout = column(spacing = Theme.PADDING_SMALL, width = contentW) {
            add(header)
            cell(contentW, listH) { cx, cy ->
                stepsList.items = steps
                depths = ActionBlocks.depths(steps)
                stepsList.render(cx, cy, contentW, listH)
            }
            row(spacing = Theme.PADDING, align = Align.CENTER, width = contentW) {
                flexSpacer()
                cell(80f, 24f) { cx, cy -> doneButton.render(cx, cy, 80f, 24f, mouseX, mouseY) }
            }
        }

        layout.draw(contentX, contentY)
    }

    private fun renderStepRow(step: ActionStep, index: Int, rowX: Float, ry: Float, rowW: Float) {
        val depth = depths.getOrElse(index) { 0 }
        val indent = depth.coerceAtLeast(0) * BLOCK_INDENT
        val rx = rowX + indent
        val rw = rowW - indent
        val hover = hovered(rx, ry, rw, stepsList.rowHeight)
        val bg = when {
            stepsList.isDragged(index) -> Theme.ACCENT.withAlpha(200)
            hover -> Theme.SURFACE_HOVER
            step.enabled -> Theme.SURFACE
            else -> Theme.SURFACE.withAlpha(120)
        }
        Render.rect(rx, ry, rw, stepsList.rowHeight, bg, Theme.RADIUS_SMALL)

        val handleX = rx + Theme.PADDING - 2f
        stepsList.grip(index, handleX, ry)

        val details = when {
            depth >= 0 -> step.summary()
            step.type === ElseStep -> actionTr("steps.else_unmatched", "not directly in an If")
            else -> actionTr("steps.end_unmatched", "nothing to close")
        }
        val text = "[${step.typeName}] $details"
        val room = rw - 180f
        Render.text(
            text,
            handleX + ScrollableList.GRIP_W,
            Render.centerY(ry, stepsList.rowHeight, Theme.FONT_BODY, Fonts.medium),
            Theme.FONT_BODY,
            when {
                depth < 0 -> Theme.DANGER
                step.enabled -> Theme.TEXT_PRIMARY
                else -> Theme.TEXT_MUTED
            },
            Fonts.medium,
            maxWidth = room,
        )

        val delX = rx + rw - 170f
        val delY = ry + 4f
        val delHover = widgets.clickable(delX, delY, 48f, 20f) {
            UiManager.confirmPopover(delX, delY, 48f, 20f) {
                // By identity, because the list might have moved.
                steps.indexOfFirst { it === step }.takeIf { it >= 0 }?.let(steps::removeAt)
            }
        }
        Render.rect(delX, delY, 48f, 20f, if (delHover) Theme.DANGER else Theme.SURFACE_ACTIVE, Theme.RADIUS_SMALL)
        Render.textCentered(actionTr("delete", "Delete"), delX + 24f, Render.centerY(delY, 20f, TextStyle.SMALL_PRIMARY_MEDIUM), TextStyle.SMALL_PRIMARY_MEDIUM)

        val editX = rx + rw - 115f
        val editY = ry + 4f
        val editHover = widgets.clickable(editX, editY, 48f, 20f) { ActionEditors.editStep(step, persist) }
        Render.rect(editX, editY, 48f, 20f, if (editHover) Theme.ACCENT else Theme.SURFACE_ACTIVE, Theme.RADIUS_SMALL)
        Render.textCentered(actionTr("edit", "Edit"), editX + 24f, Render.centerY(editY, 20f, TextStyle.SMALL_PRIMARY_MEDIUM), TextStyle.SMALL_PRIMARY_MEDIUM)

        val toggleX = rx + rw - 60f
        val toggleY = ry + 4f
        val toggleHover = widgets.clickable(toggleX, toggleY, 52f, 20f) {
            step.enabled = !step.enabled
            persist()
        }
        val toggleColor = if (step.enabled) Theme.ACCENT else Theme.SURFACE_ACTIVE
        Render.rect(toggleX, toggleY, 52f, 20f, if (toggleHover) Theme.ACCENT.withAlpha(200) else toggleColor, Theme.RADIUS_SMALL)
        val toggleText = actionTr(if (step.enabled) "on" else "off", if (step.enabled) "ON" else "OFF")
        Render.textCentered(toggleText, toggleX + 26f, Render.centerY(toggleY, 20f, TextStyle.SMALL_PRIMARY_BOLD), TextStyle.SMALL_PRIMARY_BOLD)
    }
}
