package org.blackaddons.core.ui.screen.actions.graph

import org.blackaddons.api.ui.node.*
import org.blackaddons.api.ui.screen.ContextMenu
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.core.actions.*
import org.blackaddons.core.actions.execution.ActionExecutor
import org.blackaddons.core.actions.steps.ElseStep
import org.blackaddons.core.actions.steps.EndStep
import org.blackaddons.core.config.portable.PortableImports
import org.blackaddons.core.ui.screen.actions.ActionEditors
import org.blackaddons.core.ui.screen.actions.ActionMenu
import org.blackaddons.core.ui.screen.actions.ActionsPage
import org.blackaddons.core.ui.screen.actions.actionTr

internal class ActionGraphMenus(
    private val canvas: NodeCanvas,
    private val edits: ActionGraphEdits,
    private val frames: ActionGraphGroups,
    private val currentPage: () -> ActionsPage,
    private val currentProfile: () -> ActionProfile,
    private val defaultPositions: (IntArray) -> Map<Int, FloatArray>,
    private val showNew: (Groupable) -> Unit,
) {
    private val graph get() = canvas.graph
    private val page get() = currentPage()
    private val profile get() = currentProfile()

    private fun pasteItem(x: Float, y: Float) = ContextMenu.Item(PortableImports.tr("paste", "Paste from clipboard")) { edits.paste(x, y) }

    fun contextMenu(point: CanvasPoint) {
        val node = point.node
        val link = point.link
        val group = point.group
        if (node != null && node.id !in canvas.selection) canvas.selection.set(node.id)
        val selected = if (link == null) selectionMenu(node) else emptyList()
        val canvasItems = listOf(
            ContextMenu.Item(actionTr("graph.menu.add_step", "Add step...")) { openStepMenu(point.worldX, point.worldY) },
            triggerItem(),
            pasteItem(point.worldX, point.worldY),
            ContextMenu.Separator,
            ContextMenu.Item(actionTr("graph.menu.frame", "Frame all")) { canvas.frameAll() },
        )
        val own = when {
            node != null -> emptyList()
            group != null -> groupMenu(group) + ContextMenu.Separator + canvasItems
            link != null -> listOf(
                ContextMenu.Item(actionTr("graph.menu.delete_wire", "Delete wire"), danger = true) { canvas.disconnect(link) },
            )
            else -> canvasItems
        }
        UiManager.contextMenu(if (selected.isNotEmpty() && own.isNotEmpty()) selected + ContextMenu.Separator + own else selected + own)
    }

    private fun selectionMenu(node: GraphNode?): List<ContextMenu.Entry> {
        val targets = canvas.selection.toIntArray().asList().mapNotNull(graph::node)
        if (targets.isEmpty()) return emptyList()
        val ids = targets.map { it.id }.toIntArray()
        val anyOn = targets.any(edits::isEnabled)
        val entries = ArrayList<ContextMenu.Entry>()
        if (node != null && targets.size == 1) entries += ContextMenu.Item(actionTr("graph.menu.edit", "Edit...")) { edits.edit(node) }
        entries += ContextMenu.Item(
            if (anyOn) actionTr("graph.menu.disable", "Disable") else actionTr("graph.menu.enable", "Enable"),
        ) { edits.setEnabled(targets, !anyOn) }
        val links = targets.flatMap { graph.linksOf(it.id) }.distinctBy { it.id }
        entries += ContextMenu.Item(actionTr("graph.menu.disconnect", "Disconnect all"), enabled = links.isNotEmpty()) {
            canvas.history.execute(CompoundCommand(links.filter { graph.findLink(it.spec) != null }.map { Disconnect(it.spec) }))
        }
        entries += ContextMenu.Item(actionTr("graph.menu.reset_position", "Reset position")) { canvas.moveNodesTo(defaultPositions(ids)) }
        if (targets.any { it.payload is StepPayload }) {
            entries += ContextMenu.Item(actionTr("graph.menu.copy", "Copy")) { edits.copySteps(targets) }
            entries += ContextMenu.Item(actionTr("menu.duplicate", "Duplicate")) { edits.duplicateSteps(targets) }
        }
        val triggerIds = targets.filter { it.payload is TriggerPayload }.map { it.id }.toIntArray()
        if (triggerIds.isNotEmpty()) {
            entries += ContextMenu.Separator
            entries += ContextMenu.Item(actionTr("graph.menu.group", "Group")) {
                val name = actionTr("group.new_name", "New Group")
                canvas.groupSelection(name, ActionGraphStyle.groupColor(page.groups(profile).size))?.let(::editGroup)
            }
            if (triggerIds.any { graph.groupOf(it) != null }) {
                entries += ContextMenu.Item(actionTr("graph.menu.ungroup_nodes", "Remove from group")) { canvas.setGroup(triggerIds, null) }
            }
            val refs = targets.mapNotNull { (it.payload as? TriggerPayload)?.ref }.distinctBy { it.owner }
            entries += ContextMenu.Separator
            entries += ActionMenu.sharing(refs.joinToString { ActionGraphStyle.triggerTitle(it) }, contentOf(refs.map { it.owner }))
        }
        if (targets.any { it.deletable }) {
            entries += ContextMenu.Separator
            entries += ContextMenu.Item(actionTr("graph.menu.delete", "Delete"), danger = true) {
                canvas.deleteSelection()
            }
        }
        return entries
    }

    private fun groupMenu(frame: NodeGroup): List<ContextMenu.Entry> {
        val group = frame.payload as? ActionGroup ?: return emptyList()
        return listOf(
            ContextMenu.Item(actionTr("graph.menu.edit", "Edit...")) { editGroup(frame) },
            ContextMenu.Item(
                if (group.enabled) actionTr("graph.menu.disable", "Disable") else actionTr("graph.menu.enable", "Enable"),
            ) { toggleGroup(frame) },
            ContextMenu.Item(actionTr("graph.menu.reset_position", "Reset position")) {
                canvas.moveNodesTo(defaultPositions(frame.memberIds.filter { graph.node(it) != null }.toIntArray()))
            },
            ContextMenu.Item(actionTr("graph.menu.ungroup", "Ungroup")) { canvas.ungroup(frame) },
            ContextMenu.Separator,
        ) + ActionMenu.sharing(group.name, contentOf(TriggerRef.of(profile, page).map { it.owner }.filter { it.groupId == group.id }, group))
    }

    fun toggleGroup(frame: NodeGroup) {
        val group = frame.payload as? ActionGroup ?: return
        group.enabled = !group.enabled
        ActionStorage.save()
        frames.restyle(frame, page.groups(profile))
    }

    private fun editGroup(frame: NodeGroup) {
        val group = frame.payload as? ActionGroup ?: return
        ActionEditors.editGroup(group) { frames.restyle(frame, page.groups(profile)) }
    }

    fun openAddMenu() {
        val wx = canvas.camera.toWorldX(canvas.width / 2f).toFloat()
        val wy = canvas.camera.toWorldY(canvas.height / 2f).toFloat()
        UiManager.contextMenu(
            listOf(
                ContextMenu.Item(actionTr("graph.menu.add_step", "Add step...")) { openStepMenu(wx, wy) },
                triggerItem(),
                pasteItem(wx, wy),
            ),
        )
    }

    private fun openStepMenu(x: Float, y: Float) {
        val types = ActionExecutor.availableTypes().filter { it !== EndStep && it !== ElseStep }
        UiManager.contextMenu(types.map { type -> ContextMenu.Item(type.displayName) { edits.addStep(type, x, y) } })
    }

    private fun triggerItem(): ContextMenu.Item = when (page) {
        ActionsPage.CHAT -> ContextMenu.Item(actionTr("graph.menu.new_chat", "Chat action...")) { ActionEditors.createChat() }
        ActionsPage.WAYPOINT -> ContextMenu.Item(actionTr("graph.menu.new_waypoint", "Waypoint at my position...")) {
            ActionEditors.createWaypoint()
            showNew(ActionStorage.waypoints.last())
        }
        ActionsPage.SHORTCUT -> ContextMenu.Item(actionTr("graph.menu.new_shortcut", "Shortcut...")) { ActionEditors.createShortcut() }
        ActionsPage.LOCATION -> ContextMenu.Item(actionTr("graph.menu.new_location", "Location action...")) { ActionEditors.createLocation() }
    }

    private fun contentOf(owners: List<Groupable>, group: ActionGroup? = null) = ActionProfile(
        chatActions = owners.filterIsInstanceTo(mutableListOf<ChatAction>()),
        waypoints = owners.filterIsInstanceTo(mutableListOf<WaypointDefinition>()),
        shortcuts = owners.filterIsInstanceTo(mutableListOf<ShortcutAction>()),
        locationActions = owners.filterIsInstanceTo(mutableListOf<LocationAction>()),
    ).also { content -> group?.let { page.groups(content) += it } }
}
