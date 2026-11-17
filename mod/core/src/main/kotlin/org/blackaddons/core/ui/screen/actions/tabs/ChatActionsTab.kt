package org.blackaddons.core.ui.screen.actions.tabs
import org.blackaddons.api.input.Keys
import org.blackaddons.api.ui.widget.WidgetHost
import org.blackaddons.core.actions.ActionGroup
import org.blackaddons.core.actions.ActionProfile
import org.blackaddons.core.actions.ActionStorage
import org.blackaddons.core.actions.ChatAction
import org.blackaddons.core.ui.screen.actions.ActionEditors
import org.blackaddons.core.ui.screen.actions.ActionMenu
import org.blackaddons.core.ui.screen.actions.ActionRow
import org.blackaddons.core.ui.screen.actions.ActionRowButtons
import org.blackaddons.core.ui.screen.actions.ActionsPage
import org.blackaddons.core.ui.screen.actions.RowButton
import org.blackaddons.core.ui.screen.actions.actionTr

internal class ChatActionsTab(widgets: WidgetHost) : GroupedTab<ChatAction>(widgets, ActionsPage.CHAT) {

    override val items: MutableList<ChatAction> get() = ActionStorage.chatActions

    init {
        list.emptyMessage = actionTr("chat.empty", "No chat actions. Click + Add Action to create one.")
    }

    override fun content(group: ActionGroup, members: MutableList<ChatAction>) =
        ActionProfile(chatGroups = mutableListOf(group), chatActions = members)

    override fun drawItem(item: ChatAction, index: Int, rx: Float, ry: Float, rw: Float) {
        val action = item
        val rowH = list.rowHeight
        ActionRow.background(rx, ry, rw, rowH, action.enabled)
        val title = action.pattern.ifBlank { actionTr("chat.empty_pattern", "<Empty Pattern>") }
        val delete = {
            ActionStorage.chatActions.remove(action)
            ActionStorage.save()
        }
        widgets.clickable(rx, ry, rw, rowH, Keys.MOUSE_RIGHT) {
            ActionMenu.row(
                title, ActionProfile(chatActions = mutableListOf(action)),
                edit = { ActionEditors.editChat(action) }, steps = { ActionEditors.openSteps(action.actions) }, delete,
            )
        }
        list.grip(index, rx + ActionRow.GRIP_X, ry)
        ActionRow.title(
            title,
            rx + ActionRow.TITLE_X, ry, rowH,
            maxWidth = ActionRowButtons.stripLeft(rx, rw) - (rx + ActionRow.TITLE_X) - 10f,
            enabled = action.enabled,
        )
        ActionRowButtons.draw(widgets, rx, ry, rw, action.enabled) { button ->
            when (button) {
                RowButton.DELETE -> confirmDelete(rx, ry, rw, delete = delete)
                RowButton.STEPS -> ActionEditors.openSteps(action.actions)
                RowButton.EDIT -> ActionEditors.editChat(action)
                RowButton.TOGGLE -> {
                    action.enabled = !action.enabled
                    ActionStorage.save()
                }
            }
        }
    }

    override fun onAdd() {
        ActionEditors.createChat()
    }
}
