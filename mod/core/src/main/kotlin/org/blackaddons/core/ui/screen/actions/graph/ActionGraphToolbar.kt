package org.blackaddons.core.ui.screen.actions.graph

import org.blackaddons.api.ui.layout.Align
import org.blackaddons.api.ui.layout.LayoutBuilder
import org.blackaddons.api.ui.layout.row
import org.blackaddons.api.ui.node.NodeCanvas
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.ContextMenu
import org.blackaddons.api.ui.screen.Tooltip
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.WidgetHost
import org.blackaddons.api.ui.widget.dropdown.Dropdown
import org.blackaddons.core.actions.WaypointDefinition
import org.blackaddons.core.features.impl.settings.Actions
import org.blackaddons.core.ui.screen.actions.ActionProfiles
import org.blackaddons.core.ui.screen.actions.ActionRow
import org.blackaddons.core.ui.screen.actions.ActionsPage
import org.blackaddons.core.ui.screen.actions.actionTr
import org.blackaddons.core.ui.screen.profile.ProfileMenu
import java.awt.Color

/** Top bar. Tools that don't fit fold into "...", least used first. */
internal class ActionGraphToolbar(
    widgets: WidgetHost,
    private val canvas: NodeCanvas,
    onList: () -> Unit,
    onBoard: (ActionsPage) -> Unit,
    onReset: () -> Unit,
    private val closest: () -> WaypointDefinition?,
    onClosest: (WaypointDefinition) -> Unit,
    onAdd: () -> Unit,
) {
    private val listButton = widgets.add(Button(actionTr("graph.normal_view", "Normal View")) { onList() })
    private val boardPicker = widgets.add(Dropdown(maxVisibleItems = ActionsPage.entries.size)).apply {
        items = ActionsPage.entries.map { Dropdown.Item(it.label) }
        onSelect = { onBoard(ActionsPage.entries[it]) }
    }
    private val profileButton = widgets.add(Button("") { ProfileMenu.open(ActionProfiles, {}) })
    private val fitButton = widgets.add(Button(actionTr("graph.fit", "Fit")) { canvas.frameSelection() })
    private val resetButton = widgets.add(Button(actionTr("graph.reset", "Reset Layout")) { UiManager.confirmPopover { onReset() } })
    private val undoButton = widgets.add(Button(actionTr("graph.undo", "Undo")) { canvas.history.undo() })
    private val redoButton = widgets.add(Button(actionTr("graph.redo", "Redo")) { canvas.history.redo() })
    private val mapButton = widgets.add(Button(actionTr("graph.minimap", "Map")) {
        canvas.showMinimap = !canvas.showMinimap
        Actions.graphMinimap = canvas.showMinimap
    })
    private val closestButton = widgets.add(Button(actionTr("graph.closest", "Closest Waypoint")) { closest()?.let(onClosest) })
    private val addButton = widgets.add(Button(actionTr("graph.add", "+ Add")) { onAdd() }.apply { accent = true })
    private val moreButton = widgets.add(Button("...") { openMoreMenu() })
    private val foldOrder = listOf(resetButton, mapButton, fitButton, closestButton, redoButton, undoButton)
    private var folded: List<Button> = emptyList()

    fun select(page: ActionsPage) {
        boardPicker.selectedIndex = page.ordinal
    }

    fun draw(page: ActionsPage, profileName: String, looseSteps: Int, sw: Float, mouseX: Float, mouseY: Float) {
        Render.rect(0f, 0f, sw, HEIGHT, Theme.SURFACE)
        Render.rect(0f, HEIGHT - 1f, sw, 1f, Theme.BORDER)
        undoButton.enabled = canvas.history.canUndo
        redoButton.enabled = canvas.history.canRedo
        mapButton.accent = canvas.showMinimap
        val showsClosest = page == ActionsPage.WAYPOINT
        if (showsClosest) closestButton.enabled = closest() != null
        profileButton.label = profileName
        val loose = if (looseSteps > 0) actionTr("graph.loose", "%d steps not attached to a trigger", looseSteps) else null

        val gap = Theme.PADDING_SMALL
        val boardW = boardPicker.preferredWidth()
        val tools = listOfNotNull(closestButton.takeIf { showsClosest }, undoButton, redoButton, fitButton, resetButton, mapButton)
        val free = sw - Theme.PADDING * 2f - widthOf(listButton) - boardW - widthOf(addButton) - gap * 4f
        val profileNatural = profileButton.naturalWidth().coerceIn(PROFILE_MIN_W, PROFILE_MAX_W)
        val shown = shownTools(tools, free - profileNatural)
        folded = tools - shown.toSet()
        val spare = free - toolsWidth(shown, folded.isNotEmpty())
        val profileW = minOf(profileNatural, spare).coerceAtLeast(PROFILE_MIN_W)
        val tagRoom = spare - profileW - gap * 2f
        val tag = loose?.takeIf { tagRoom >= TAG_MIN_W }?.let {
            Render.ellipsize(it, tagRoom - ActionRow.tagWidth(""), Theme.FONT_SMALL, Fonts.medium)
        }
        row(spacing = gap, align = Align.CENTER, width = sw - Theme.PADDING * 2f, height = HEIGHT) {
            button(listButton, mouseX, mouseY)
            cell(boardW, BUTTON_H) { cx, cy -> boardPicker.render(cx, cy, boardW, BUTTON_H, mouseX, mouseY) }
            cell(profileW, BUTTON_H) { cx, cy -> profileButton.render(cx, cy, profileW, BUTTON_H, mouseX, mouseY) }
            if (tag != null) {
                spacer()
                cell(ActionRow.tagWidth(tag), TAG_H) { cx, cy ->
                    ActionRow.tag(tag, cx, cy, TAG_H, LOOSE_COLOR)
                    Tooltip.showOver(cx, cy, ActionRow.tagWidth(tag), TAG_H, loose.takeIf { it != tag })
                }
            }
            flexSpacer()
            for (b in shown) button(b, mouseX, mouseY)
            if (folded.isNotEmpty()) button(moreButton, mouseX, mouseY)
            button(addButton, mouseX, mouseY)
        }.draw(Theme.PADDING, 0f)
    }

    fun renderOverlay(mouseX: Float, mouseY: Float) = boardPicker.renderOverlay(mouseX, mouseY)

    private fun LayoutBuilder.button(b: Button, mouseX: Float, mouseY: Float) {
        val w = widthOf(b)
        cell(w, BUTTON_H) { cx, cy -> b.render(cx, cy, w, BUTTON_H, mouseX, mouseY) }
    }

    private fun widthOf(b: Button): Float = maxOf(b.naturalWidth() + Theme.PADDING, MIN_BUTTON_W)

    private fun toolsWidth(shown: List<Button>, anyFolded: Boolean): Float =
        shown.sumOf { (widthOf(it) + Theme.PADDING_SMALL).toDouble() }.toFloat() +
            if (anyFolded) widthOf(moreButton) + Theme.PADDING_SMALL else 0f

    private fun shownTools(tools: List<Button>, room: Float): List<Button> {
        val shown = tools.toMutableList()
        for (b in foldOrder) {
            if (toolsWidth(shown, shown.size < tools.size) <= room) break
            shown.remove(b)
        }
        return shown
    }

    private fun openMoreMenu() = UiManager.contextMenu(folded.map { b ->
        ContextMenu.Item(b.label, enabled = b.enabled, checked = if (b === mapButton) ({ canvas.showMinimap }) else null) { b.onClick() }
    })

    companion object {
        const val HEIGHT = 30f
        private const val BUTTON_H = 20f
        private const val MIN_BUTTON_W = 44f
        private const val TAG_H = 18f
        private const val TAG_MIN_W = 60f
        private const val PROFILE_MIN_W = 80f
        private const val PROFILE_MAX_W = 160f
        private val LOOSE_COLOR = Color(0xE0A050)
    }
}
