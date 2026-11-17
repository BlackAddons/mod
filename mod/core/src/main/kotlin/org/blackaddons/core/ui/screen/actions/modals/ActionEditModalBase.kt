package org.blackaddons.core.ui.screen.actions.modals
import org.blackaddons.api.ui.layout.Align
import org.blackaddons.api.ui.layout.LayoutBuilder
import org.blackaddons.api.ui.layout.column
import org.blackaddons.api.ui.layout.row
import org.blackaddons.api.ui.screen.ModalPanel
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.Scroll
import org.blackaddons.api.util.I18n
import org.blackaddons.core.actions.ActionStep
import org.blackaddons.core.ui.screen.actions.actionTr
import org.blackaddons.core.ui.screen.actions.fields.ActionFeedbackFields

/**
 * Shared shell for the chat-trigger and waypoint editors: scrolling form, [ActionFeedbackFields], Manage Steps and a
 * Delete/Cancel/Save footer. Subclasses give [formSection], [commit] and [remove]. Steps are edited on a copy and only
 * handed over in [commit].
 */
internal abstract class ActionEditModalBase(
    private val onSaved: () -> Unit,
) : ModalPanel(actionTr("edit_action", "Edit Action")) {

    override val panelWidth: Float
        get() = widthShare(0.82f, min = 560f)

    override val panelHeight: Float
        get() = minOf(formHeight, (UiManager.screenHeight - 30f).coerceAtLeast(280f))

    protected abstract val formHeight: Float

    protected val feedback = ActionFeedbackFields()
    protected val workingSteps = mutableListOf<ActionStep>()

    private val scroll = widgets.add(Scroll())

    private val manageStepsBtn = widgets.add(Button("") {
        UiManager.push(ActionStepsModal(workingSteps) { updateStepsLabel() })
    })

    private val saveButton = widgets.add(Button(actionTr("save", "Save")) {
        commit()
        UiManager.pop()
        onSaved()
    }.apply { accent = true })

    private val deleteButton = widgets.add(Button(actionTr("delete", "Delete")) {
        UiManager.confirmPopover(contentX, contentY + contentH - 26f, 70f, 24f) {
            remove()
            UiManager.pop()
            onSaved()
        }
    })

    private val cancelButton = widgets.add(Button(actionTr("cancel", "Cancel")) { UiManager.pop() })

    protected abstract fun formSection(builder: LayoutBuilder, formW: Float, mouseX: Float, mouseY: Float)

    protected abstract fun commit()

    protected abstract fun remove()

    protected open fun renderOverlays(mouseX: Float, mouseY: Float) = Unit

    /** Not in `init`, because subclass fields aren't constructed yet. */
    protected fun ready() {
        feedback.attachTo(widgets)
        updateStepsLabel()
    }

    protected fun updateStepsLabel() {
        manageStepsBtn.label = actionTr("manage_steps", "Manage Action Steps (%s) ->", I18n.steps(workingSteps.size))
    }

    override fun renderContent(mouseX: Float, mouseY: Float) {
        val scrollableH = scrollableHeight()
        val formW = if (scroll.maxScroll > 0f) contentW - 10f else contentW

        val form = column(spacing = Theme.PADDING_SMALL, width = formW) {
            formSection(this, formW, mouseX, mouseY)
            feedback.section(this, formW, mouseX, mouseY)
        }

        scroll.render(contentX, contentY, contentW, scrollableH, form.height) { offset ->
            form.draw(contentX, contentY - offset)
        }

        val bottomY = contentY + contentH - FIXED_BOTTOM_H
        manageStepsBtn.render(contentX, bottomY, contentW, 26f, mouseX, mouseY)

        val footer = row(spacing = Theme.PADDING, align = Align.CENTER, width = contentW) {
            cell(70f, 24f) { cx, cy -> deleteButton.render(cx, cy, 70f, 24f, mouseX, mouseY) }
            flexSpacer()
            cell(80f, 24f) { cx, cy -> cancelButton.render(cx, cy, 80f, 24f, mouseX, mouseY) }
            cell(80f, 24f) { cx, cy -> saveButton.render(cx, cy, 80f, 24f, mouseX, mouseY) }
        }
        footer.draw(contentX, bottomY + 32f)

        feedback.renderOverlays(mouseX, mouseY)
        renderOverlays(mouseX, mouseY)
    }

    // Escape saves, because there's no explicit apply.
    override fun onEscape() {
        commit()
        onSaved()
    }

    private fun scrollableHeight(): Float = contentH - FIXED_BOTTOM_H - 6f

    private companion object {
        const val FIXED_BOTTOM_H = 58f
    }
}
