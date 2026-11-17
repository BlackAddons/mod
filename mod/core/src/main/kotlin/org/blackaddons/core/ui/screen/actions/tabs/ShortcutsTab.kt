package org.blackaddons.core.ui.screen.actions.tabs
import org.blackaddons.api.Platform
import org.blackaddons.api.input.InputType
import org.blackaddons.api.input.Keys
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.WidgetHost
import org.blackaddons.api.util.I18n
import org.blackaddons.core.actions.ActionGroup
import org.blackaddons.core.actions.ActionProfile
import org.blackaddons.core.actions.ActionStorage
import org.blackaddons.core.actions.ShortcutAction
import org.blackaddons.core.ui.screen.actions.ActionEditors
import org.blackaddons.core.ui.screen.actions.ActionMenu
import org.blackaddons.core.ui.screen.actions.ActionRow
import org.blackaddons.core.ui.screen.actions.ActionRowButtons
import org.blackaddons.core.ui.screen.actions.ActionsPage
import org.blackaddons.core.ui.screen.actions.RowButton
import org.blackaddons.core.ui.screen.actions.actionTr
import java.awt.Color

internal class ShortcutsTab(widgets: WidgetHost) : GroupedTab<ShortcutAction>(widgets, ActionsPage.SHORTCUT) {

    override val items: MutableList<ShortcutAction> get() = ActionStorage.shortcuts

    init {
        list.emptyMessage = actionTr("shortcut.empty", "No shortcuts. Click + Add Action to create a command alias or keybind.")
    }

    override fun content(group: ActionGroup, members: MutableList<ShortcutAction>) =
        ActionProfile(shortcutGroups = mutableListOf(group), shortcuts = members)

    override fun drawItem(item: ShortcutAction, index: Int, rx: Float, ry: Float, rw: Float) {
        val shortcut = item
        val rowH = list.rowHeight
        ActionRow.background(rx, ry, rw, rowH, shortcut.enabled)

        val name = shortcut.name.ifBlank { shortcut.alias.ifBlank { actionTr("shortcut.fallback", "Shortcut") } }
        val pillLimit = ActionRowButtons.stripLeft(rx, rw) - 10f
        list.grip(index, rx + ActionRow.GRIP_X, ry)
        var x = ActionRow.title(name, rx + ActionRow.TITLE_X, ry, rowH, TITLE_W, shortcut.enabled, Fonts.bold)
        val delete = {
            ActionStorage.shortcuts.remove(shortcut)
            ActionStorage.save()
        }
        widgets.clickable(rx, ry, rw, rowH, Keys.MOUSE_RIGHT) {
            ActionMenu.row(
                name, ActionProfile(shortcuts = mutableListOf(shortcut)),
                edit = { ActionEditors.editShortcut(shortcut) }, steps = { ActionEditors.openSteps(shortcut.actions) }, delete,
            )
        }

        if (shortcut.alias.isNotBlank()) {
            x = ActionRow.pill("/${shortcut.alias}", x, ry, rowH, Theme.ACCENT.withAlpha(150), pillLimit)
        }
        if (shortcut.isBound) {
            x = ActionRow.pill("[${keyLabel(shortcut)}]", x, ry, rowH, KEY_PILL, pillLimit)
        }
        ActionRow.pill(I18n.steps(shortcut.actions.size), x, ry, rowH, Theme.SURFACE_ACTIVE, pillLimit)

        ActionRowButtons.draw(widgets, rx, ry, rw, shortcut.enabled) { button ->
            when (button) {
                RowButton.DELETE -> confirmDelete(rx, ry, rw, delete = delete)
                RowButton.STEPS -> ActionEditors.openSteps(shortcut.actions)
                RowButton.EDIT -> ActionEditors.editShortcut(shortcut)
                RowButton.TOGGLE -> {
                    shortcut.enabled = !shortcut.enabled
                    ActionStorage.save()
                }
            }
        }
    }

    private fun keyLabel(shortcut: ShortcutAction): String =
        if (shortcut.key.type == InputType.MOUSE) {
            when (shortcut.key.code) {
                0 -> actionTr("shortcut.mouse_left_short", "LMB")
                1 -> actionTr("shortcut.mouse_right_short", "RMB")
                2 -> actionTr("shortcut.mouse_middle_short", "MMB")
                else -> actionTr("shortcut.mouse_short", "M%d", shortcut.key.code + 1)
            }
        } else {
            runCatching { Platform.keyNames.keyName(shortcut.key.code).uppercase() }.getOrElse { actionTr("shortcut.key_short", "KEY") }
        }

    override fun onAdd() {
        ActionEditors.createShortcut()
    }

    private companion object {
        const val TITLE_W = 140f
        val KEY_PILL = Color(120, 60, 180, 180)
    }
}
