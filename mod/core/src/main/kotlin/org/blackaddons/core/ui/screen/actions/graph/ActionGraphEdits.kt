package org.blackaddons.core.ui.screen.actions.graph

import org.blackaddons.api.Platform
import org.blackaddons.api.actions.StepType
import org.blackaddons.api.ui.node.*
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.core.actions.ActionPortable
import org.blackaddons.core.actions.ActionStorage
import org.blackaddons.core.actions.execution.ActionExecutor
import org.blackaddons.core.actions.steps.BlockStep
import org.blackaddons.core.actions.steps.EndStep
import org.blackaddons.core.config.portable.PortableImports
import org.blackaddons.core.ui.screen.actions.ActionEditors
import org.blackaddons.core.ui.screen.actions.modals.*

/** Node edits (add, copy/paste, edit, toggle). [commit] saves. [reload] rebuilds after a trigger's editor saved. */
internal class ActionGraphEdits(
    private val canvas: NodeCanvas,
    private val sockets: ActionGraphSocketSet,
    private val commit: () -> Unit,
    private val reload: () -> Unit,
) {
    private val graph get() = canvas.graph

    fun selectedNodes(): List<GraphNode> = canvas.selection.toIntArray().asList().mapNotNull(graph::node)

    fun edit(node: GraphNode) {
        when (val payload = node.payload) {
            is StepPayload -> UiManager.push(StepEditModal(payload.step, inGraph = true) { afterStepEdit(node) })
            is TriggerPayload -> editTrigger(node, payload.ref)
        }
    }

    private fun editTrigger(node: GraphNode, ref: TriggerRef) {
        val refresh = { if (node.isAttached) reload() }
        when (ref) {
            is TriggerRef.Chat -> ActionEditors.editChat(ref.action, refresh)
            is TriggerRef.Waypoint -> ActionEditors.editWaypoint(ref.waypoint, refresh)
            is TriggerRef.Shortcut -> ActionEditors.editShortcut(ref.shortcut, refresh)
            is TriggerRef.Location -> ActionEditors.editLocation(ref.action, refresh)
        }
    }

    fun afterPropertyEdit(node: GraphNode) {
        when (val payload = node.payload) {
            is StepPayload -> {
                ActionGraphStyle.style(node)
                ActionStorage.save()
            }
            is TriggerPayload -> {
                val ref = payload.ref
                graph.nodes().filter { (it.payload as? TriggerPayload)?.ref?.owner === ref.owner }.forEach(ActionGraphStyle::style)
                ActionStorage.save()
                when (ref) {
                    is TriggerRef.Shortcut -> duplicateKeyOf(ref.shortcut)?.let { warnDuplicateKey(it, bindingLabel(ref.shortcut.key)) }
                    is TriggerRef.Location -> duplicateFiltersOf(ref.action)?.let(::warnDuplicateFilters)
                    else -> Unit
                }
            }
        }
    }

    private fun afterStepEdit(node: GraphNode) {
        if (!node.isAttached) return
        val payload = node.payload as? StepPayload ?: return
        val step = payload.step
        val isBlock = step.type is BlockStep
        val wasBlock = payload.lastType is BlockStep
        if (isBlock != wasBlock || step.type !== payload.lastType) {
            graph.setSockets(node.id, sockets.stepInputs, sockets.outputsFor(step.type))
            if (isBlock != wasBlock) {
                payload.end = if (isBlock) ActionExecutor.newStep(EndStep) else null
                canvas.history.clear()
            }
        }
        payload.lastType = step.type
        ActionGraphStyle.style(node)
        commit()
    }

    fun addStep(type: StepType<*>, x: Float, y: Float) {
        val step = ActionExecutor.newStep(type)
        val end = if (type is BlockStep) ActionExecutor.newStep(EndStep) else null
        val node = canvas.addNode(sockets.stepInputs, sockets.outputsFor(type), x, y) { payload = StepPayload(step, end) }
        ActionGraphStyle.style(node)
    }

    fun copySteps(nodes: List<GraphNode>) {
        val copied = ActionGraphClipboard.copy(graph, nodes) ?: return
        Platform.clipboard.copy(ActionGraphClipboard.write(copied))
    }

    fun duplicateSteps(nodes: List<GraphNode>) {
        val copied = ActionGraphClipboard.copy(graph, nodes) ?: return
        val steps = nodes.filter { it.payload is StepPayload }
        pasteSteps(ActionGraphClipboard.detached(copied), steps.minOf { it.x } + DUPLICATE_OFFSET, steps.minOf { it.y } + DUPLICATE_OFFSET)
    }

    /** Steps paste at ([x], [y]). Anything else goes to the actions import. */
    fun paste(x: Float, y: Float) {
        val text = Platform.clipboard.paste()
        val copied = ActionGraphClipboard.read(text)
        if (copied != null) pasteSteps(copied, x, y) else PortableImports.open(ActionPortable, text)
    }

    private fun pasteSteps(copied: CopiedSteps, x: Float, y: Float) {
        val nodes = copied.steps.mapIndexed { i, step ->
            val end = if (step.type is BlockStep) ActionExecutor.newStep(EndStep) else null
            val at = copied.positions[i]
            graph.addNode(sockets.stepInputs, sockets.outputsFor(step.type), x + at[0], y + at[1]) { payload = StepPayload(step, end) }
                .also(ActionGraphStyle::style)
        }
        val wires = copied.links.mapNotNull { (from, slot, to) ->
            LinkSpec(nodes[from].id, slot, nodes[to].id, 0).takeIf(canvas::canConnect)?.let(::Connect)?.also { it.apply(graph) }
        }
        canvas.selection.clear()
        nodes.forEach { canvas.selection.add(it.id) }
        canvas.history.record(CompoundCommand(nodes.map(::AddNode) + wires))
    }

    fun toggle(node: GraphNode) = setEnabled(listOf(node), !isEnabled(node))

    fun isEnabled(node: GraphNode): Boolean = when (val payload = node.payload) {
        is StepPayload -> payload.step.enabled
        is TriggerPayload -> payload.ref.enabled
        else -> true
    }

    fun setEnabled(nodes: List<GraphNode>, on: Boolean) {
        for (node in nodes) when (val payload = node.payload) {
            is StepPayload -> payload.step.enabled = on
            is TriggerPayload -> payload.ref.enabled = on
        }
        val triggerChanged = nodes.any { it.payload is TriggerPayload }
        if (triggerChanged) graph.nodes().filter { it.payload is TriggerPayload }.forEach(ActionGraphStyle::style)
        nodes.filter { it.payload is StepPayload }.forEach(ActionGraphStyle::style)
        ActionStorage.save()
    }

    private companion object {
        const val DUPLICATE_OFFSET = 32f
    }
}
