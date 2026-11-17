package org.blackaddons.core.ui.screen.actions.modals
import org.blackaddons.api.Platform
import org.blackaddons.api.input.InputBinding
import org.blackaddons.api.input.InputType
import org.blackaddons.api.input.Keys
import org.blackaddons.api.ui.layout.Align
import org.blackaddons.api.ui.layout.column
import org.blackaddons.api.ui.screen.ModalPanel
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.toast.ToastKind
import org.blackaddons.api.ui.toast.Toasts
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.KeyInput
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.text.TextInput
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.cleanCommand
import org.blackaddons.core.actions.ActionStep
import org.blackaddons.core.actions.ActionStorage
import org.blackaddons.core.actions.ShortcutAction
import org.blackaddons.core.actions.steps.SendMessageStep
import org.blackaddons.core.ui.screen.actions.actionTr
import org.blackaddons.core.ui.screen.actions.fields.GroupPicker

internal class ShortcutActionModal(
    private val shortcut: ShortcutAction,
    private val isNew: Boolean = false,
    private val onSaved: () -> Unit = {},
) : ModalPanel(actionTr(if (isNew) "shortcut.new" else "shortcut.edit", if (isNew) "New Shortcut" else "Edit Shortcut")) {

    override val panelWidth: Float
        get() = fitWidth(440f, min = 320f)

    override val panelHeight: Float
        get() = fitHeight(404f, min = 280f, margin = 30f)


    private val nameInput = widgets.add(TextInput(placeholder = actionTr("shortcut.name_placeholder", "Shortcut Name (e.g. Warp Forge)")).apply {
        text = shortcut.name
    })

    private val aliasInput = widgets.add(TextInput(placeholder = actionTr("shortcut.alias_placeholder", "Command Alias without / (e.g. wf)")).apply {
        text = shortcut.alias
    })

    private val group = GroupPicker(widgets, ActionStorage.shortcutGroups, shortcut.groupId)

    private var currentKey = shortcut.key
    private var isListeningKey = false

    private val keybindButton = widgets.add(Button("") {
        isListeningKey = true
        updateKeybindLabel()
    })

    private val unbindButton = widgets.add(Button(actionTr("shortcut.unbind", "Unbind")) {
        currentKey = InputBinding.NONE
        isListeningKey = false
        updateKeybindLabel()
    })

    private val quickCommandInput = widgets.add(TextInput(placeholder = actionTr("shortcut.quick_command_placeholder", "Quick Command (e.g. /warp forge)")).apply {
        text = shortcut.actions.firstNotNullOfOrNull { it.paramsOf(SendMessageStep) }?.message ?: ""
    })

    private val stepsButton = widgets.add(Button("") {
        syncQuickCommand()
        UiManager.push(ActionStepsModal(shortcut.actions) {
            updateStepsLabel()
        })
    })

    private val saveButton = widgets.add(Button(actionTr("save", "Save")) {
        save()
        UiManager.pop()
    }).apply { accent = true }

    init {
        updateKeybindLabel()
        updateStepsLabel()
    }

    private fun updateStepsLabel() {
        stepsButton.label = actionTr("configure_steps", "Configure Action Steps (%s)", I18n.steps(shortcut.actions.size))
    }

    private fun keyLabel(): String =
        if (isListeningKey) actionTr("shortcut.press_key", "Press a key / click...") else bindingLabel(currentKey)

    private fun updateKeybindLabel() {
        keybindButton.label = actionTr("shortcut.keybind", "Keybind: %s", keyLabel())
    }

    private fun syncQuickCommand() {
        val cmd = quickCommandInput.text.trim()
        val existing = shortcut.actions.firstOrNull { it.type === SendMessageStep }
        if (cmd.isNotEmpty()) {
            val normalizedCmd = if (cmd.startsWith("/")) cmd else "/$cmd"
            if (existing != null) {
                existing.paramsOf(SendMessageStep)?.message = normalizedCmd
            } else {
                shortcut.actions.add(0, ActionStep.of(SendMessageStep, SendMessageStep.Params(normalizedCmd)))
            }
        } else if (existing != null) {
            shortcut.actions.remove(existing)
        }
    }

    private fun save() {
        shortcut.name = nameInput.text.trim().ifEmpty {
            aliasInput.text.trim().ifEmpty { actionTr("shortcut.fallback", "Shortcut") }
        }
        shortcut.alias = aliasInput.text.cleanCommand()
        shortcut.key = currentKey
        shortcut.onlyInGame = true
        shortcut.groupId = group.selected

        syncQuickCommand()
        val duplicate = duplicateKeyOf(shortcut)

        if (isNew && !ActionStorage.shortcuts.contains(shortcut)) {
            ActionStorage.shortcuts.add(shortcut)
        }
        ActionStorage.save()
        duplicate?.let { warnDuplicateKey(it, keyLabel()) }
        onSaved()
    }

    override fun renderContent(mouseX: Float, mouseY: Float) {
        val form = column(spacing = Theme.PADDING_SMALL, width = contentW) {
            text(actionTr("shortcut.name", "Shortcut Name:"), TextStyle.SMALL_MUTED_MEDIUM)
            cell(contentW, 22f) { cx, cy -> nameInput.render(cx, cy, contentW, 22f, mouseX, mouseY) }

            text(actionTr("shortcut.alias", "Command Alias (without /):"), TextStyle.SMALL_MUTED_MEDIUM)
            cell(contentW, 22f) { cx, cy -> aliasInput.render(cx, cy, contentW, 22f, mouseX, mouseY) }

            group.section(this, contentW, mouseX, mouseY)

            text(actionTr("shortcut.hotkey", "Keybind Hotkey:"), TextStyle.SMALL_MUTED_MEDIUM)
            row(spacing = Theme.PADDING_SMALL, align = Align.CENTER, width = contentW) {
                val btnW = contentW - 80f - Theme.PADDING_SMALL
                cell(btnW, 22f) { cx, cy -> keybindButton.render(cx, cy, btnW, 22f, mouseX, mouseY) }
                cell(80f, 22f) { cx, cy -> unbindButton.render(cx, cy, 80f, 22f, mouseX, mouseY) }
            }

            text(actionTr("quick_command", "Quick Command:"), TextStyle.SMALL_MUTED_MEDIUM)
            cell(contentW, 22f) { cx, cy -> quickCommandInput.render(cx, cy, contentW, 22f, mouseX, mouseY) }

            cell(contentW, 24f) { cx, cy -> stepsButton.render(cx, cy, contentW, 24f, mouseX, mouseY) }
        }

        form.draw(contentX, contentY)
        group.renderOverlay(mouseX, mouseY)

        val saveY = contentY + contentH - 26f
        saveButton.render(contentX, saveY, contentW, 26f, mouseX, mouseY)
    }

    override fun onEscape() {
        save()
    }

    override fun contentClicked(mouse: MouseButtonInput): Boolean {
        if (isListeningKey) {
            currentKey = InputBinding.mouse(mouse.button)
            isListeningKey = false
            updateKeybindLabel()
            return true
        }

        return widgets.mouseClicked(mouse)
    }

    override fun keyPressed(key: KeyInput): Boolean {
        if (isListeningKey) {
            if (key.code == Keys.ESCAPE) {
                isListeningKey = false
            } else if (key.code == Keys.DELETE || key.code == Keys.BACKSPACE) {
                currentKey = InputBinding.NONE
                isListeningKey = false
            } else {
                currentKey = InputBinding.keyboard(key.code)
                isListeningKey = false
            }
            updateKeybindLabel()
            return true
        }
        return super.keyPressed(key)
    }
}

internal fun bindingLabel(key: InputBinding): String = when {
    !key.isBound -> actionTr("none_upper", "NONE")
    key.type == InputType.MOUSE -> when (key.code) {
        0 -> actionTr("shortcut.mouse_left", "MOUSE LEFT")
        1 -> actionTr("shortcut.mouse_right", "MOUSE RIGHT")
        2 -> actionTr("shortcut.mouse_middle", "MOUSE MIDDLE")
        else -> actionTr("shortcut.mouse_button", "MOUSE %d", key.code + 1)
    }
    else -> runCatching {
        Platform.keyNames.keyName(key.code).uppercase()
    }.getOrElse { actionTr("shortcut.key", "KEY %d", key.code) }
}

internal fun duplicateKeyOf(shortcut: ShortcutAction): ShortcutAction? =
    if (!shortcut.isBound) null else ActionStorage.shortcuts.firstOrNull { it.id != shortcut.id && it.isBound && it.key == shortcut.key }

internal fun warnDuplicateKey(duplicate: ShortcutAction, keyLabel: String) {
    Toasts.show(
        actionTr("shortcut.duplicate_title", "Duplicate shortcut keybind"),
        actionTr("shortcut.duplicate_message", "%s also uses %s.", duplicate.name.ifBlank { actionTr("shortcut.another", "Another shortcut") }, keyLabel),
        ToastKind.ERROR,
    )
}
