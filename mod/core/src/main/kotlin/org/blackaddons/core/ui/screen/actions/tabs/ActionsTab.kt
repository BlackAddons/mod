package org.blackaddons.core.ui.screen.actions.tabs
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.widget.ScrollableList
import org.blackaddons.core.ui.screen.actions.ActionRow
import org.blackaddons.core.ui.screen.actions.ActionRowButtons
import org.blackaddons.core.ui.screen.actions.RowButton

/**
 * One Actions tab: owns its list, row drawing and clicks. An interface, because tabs share no state (shared drawing is
 * in [ActionRow]/[ActionRowButtons]).
 */
internal interface ActionsTab {

    val list: ScrollableList<*>

    fun render(x: Float, y: Float, w: Float, h: Float)

    fun onAdd()

    fun addGroup()

    fun confirmDelete(rx: Float, ry: Float, rw: Float, withSteps: Boolean = true, delete: () -> Unit) {
        val x = ActionRowButtons.xOf(RowButton.DELETE, rx, rw, withSteps)
        UiManager.confirmPopover(x, ActionRowButtons.topOf(ry), 48f, ActionRowButtons.height, onConfirm = delete)
    }
}
