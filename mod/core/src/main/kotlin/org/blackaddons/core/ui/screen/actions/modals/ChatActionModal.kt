package org.blackaddons.core.ui.screen.actions.modals
import org.blackaddons.api.ui.layout.Align
import org.blackaddons.api.ui.layout.LayoutBuilder
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.text.TextInput
import org.blackaddons.core.actions.ActionStorage
import org.blackaddons.core.actions.ChatAction
import org.blackaddons.core.ui.screen.actions.actionTr
import org.blackaddons.core.ui.screen.actions.fields.GroupPicker

internal class ChatActionModal(
    private val action: ChatAction,
    onSaved: () -> Unit = {},
) : ActionEditModalBase(onSaved) {

    override val modalTitle: String get() = actionTr("chat.edit", "Edit Chat Action")
    override val formHeight: Float get() = 532f

    private var isRegex = action.isRegex

    private val patternInput = TextInput(placeholder = actionTr("chat.pattern_placeholder", "Pattern / Message")).apply { text = action.pattern }

    private val regexToggle = Button("") {
        isRegex = !isRegex
        updateLabels()
    }

    private var ignoreOwn = action.ignoreOwnMessages

    private val group = GroupPicker(widgets, ActionStorage.chatGroups, action.groupId)

    private val ignoreOwnToggle = Button("") {
        ignoreOwn = !ignoreOwn
        updateLabels()
    }

    init {
        widgets.add(patternInput)
        widgets.add(regexToggle)
        widgets.add(ignoreOwnToggle)
        workingSteps.addAll(action.actions)
        feedback.readFrom(action)
        updateLabels()
        ready()
    }

    private fun updateLabels() {
        regexToggle.label = actionTr("chat.regex", "Regex: %s", actionTr(if (isRegex) "on" else "off", if (isRegex) "ON" else "OFF"))
        ignoreOwnToggle.label = actionTr("chat.ignore_own", "Ignore My Messages: %s", actionTr(if (ignoreOwn) "on" else "off", if (ignoreOwn) "ON" else "OFF"))
    }

    override fun formSection(builder: LayoutBuilder, formW: Float, mouseX: Float, mouseY: Float) = with(builder) {
        text(actionTr("chat.trigger_pattern", "Trigger Pattern:"), TextStyle.SMALL_MUTED_MEDIUM)
        row(spacing = Theme.PADDING_SMALL, align = Align.CENTER, width = formW) {
            cell(formW - 100f, 22f) { cx, cy -> patternInput.render(cx, cy, formW - 100f, 22f, mouseX, mouseY) }
            cell(95f, 22f) { cx, cy -> regexToggle.render(cx, cy, 95f, 22f, mouseX, mouseY) }
        }
        cell(formW, 22f) { cx, cy -> ignoreOwnToggle.render(cx, cy, formW, 22f, mouseX, mouseY) }
        group.section(this, formW, mouseX, mouseY)
    }

    override fun renderOverlays(mouseX: Float, mouseY: Float) = group.renderOverlay(mouseX, mouseY)

    override fun commit() {
        action.pattern = patternInput.text.trim()
        action.isRegex = isRegex
        action.ignoreOwnMessages = ignoreOwn
        action.groupId = group.selected
        feedback.writeTo(action)
        action.actions.clear()
        action.actions.addAll(workingSteps)
        ActionStorage.save()
    }

    override fun remove() {
        ActionStorage.chatActions.remove(action)
        ActionStorage.save()
    }
}
