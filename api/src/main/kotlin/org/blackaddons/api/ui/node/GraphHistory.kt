package org.blackaddons.api.ui.node

import java.awt.Color

/** One undoable edit. [apply] and [revert] must be exact inverses and touch only what changed. */
interface GraphCommand {
    fun apply(graph: NodeGraph)
    fun revert(graph: NodeGraph)

    /** Adds/removes nodes or wires rather than only moving them. */
    val structural: Boolean get() = true

    /** Combined with [next] when they read as one edit (consecutive nudges), else null. */
    fun merge(next: GraphCommand): GraphCommand? = null
}

/** [nudge] marks a keyboard step: consecutive nudges of the same nodes undo as one. */
class MoveNodes(val ids: IntArray, val dx: Float, val dy: Float, val nudge: Boolean = false) : GraphCommand {
    override val structural: Boolean get() = false
    override fun apply(graph: NodeGraph) = graph.moveBy(ids, dx, dy)
    override fun revert(graph: NodeGraph) = graph.moveBy(ids, -dx, -dy)

    override fun merge(next: GraphCommand): GraphCommand? =
        if (nudge && next is MoveNodes && next.nudge && next.ids.contentEquals(ids)) {
            MoveNodes(ids, dx + next.dx, dy + next.dy, nudge = true)
        } else {
            null
        }
}

/** [node] must be detached. */
class AddNode(val node: GraphNode) : GraphCommand {
    override fun apply(graph: NodeGraph) {
        if (!node.isAttached) graph.restoreNode(node)
    }

    override fun revert(graph: NodeGraph) {
        graph.removeNode(node.id)
    }
}

/** Undo restores wires last so their ends exist. */
class RemoveNodes(private val ids: IntArray) : GraphCommand {
    private val removed = ArrayList<GraphNode>()
    private val wires = LinkedHashSet<LinkSpec>()

    override fun apply(graph: NodeGraph) {
        removed.clear()
        wires.clear()
        for (id in ids) {
            val (node, links) = graph.removeNode(id) ?: continue
            removed += node
            wires += links
        }
    }

    override fun revert(graph: NodeGraph) {
        removed.forEach { if (!it.isAttached) graph.restoreNode(it) }
        wires.forEach { graph.connect(it.from, it.fromSlot, it.to, it.toSlot) }
    }
}

/** Undo also restores wires a single-wire socket gave up. */
class Connect(val spec: LinkSpec) : GraphCommand {
    private var replaced: List<LinkSpec> = emptyList()
    private var created = false

    override fun apply(graph: NodeGraph) {
        val existed = graph.findLink(spec) != null
        val (_, gaveUp) = graph.connect(spec.from, spec.fromSlot, spec.to, spec.toSlot) ?: return
        created = !existed
        replaced = gaveUp
    }

    override fun revert(graph: NodeGraph) {
        if (created) graph.findLink(spec)?.let { graph.disconnect(it.id) }
        replaced.forEach { graph.connect(it.from, it.fromSlot, it.to, it.toSlot) }
    }
}

class Disconnect(val spec: LinkSpec) : GraphCommand {
    override fun apply(graph: NodeGraph) {
        graph.findLink(spec)?.let { graph.disconnect(it.id) }
    }

    override fun revert(graph: NodeGraph) {
        graph.connect(spec.from, spec.fromSlot, spec.to, spec.toSlot)
    }
}

/** Null takes them out of any group; undo restores each one's old group. */
class SetGroup(private val ids: IntArray, private val group: NodeGroup?) : GraphCommand {
    private val previous = arrayOfNulls<NodeGroup>(ids.size)

    override val structural: Boolean get() = false

    override fun apply(graph: NodeGraph) {
        ids.forEachIndexed { i, id ->
            previous[i] = graph.groupOf(id)
            graph.setGroup(id, group)
        }
    }

    override fun revert(graph: NodeGraph) {
        for (i in ids.indices.reversed()) graph.setGroup(ids[i], previous[i]?.takeIf { it.isAttached })
    }
}

class AddGroup(private val title: String, private val color: Color, private val ids: IntArray) : GraphCommand {
    private var group: NodeGroup? = null
    private var join: SetGroup? = null

    override val structural: Boolean get() = false

    val created: NodeGroup? get() = group

    override fun apply(graph: NodeGraph) {
        val existing = group
        val target = if (existing == null) graph.addGroup(title, color) else existing.also { graph.restoreGroup(it) }
        group = target
        join = SetGroup(ids, target).also { it.apply(graph) }
    }

    override fun revert(graph: NodeGraph) {
        join?.revert(graph)
        group?.let { graph.removeGroup(it.id) }
    }
}

/** Its nodes stay, ungrouped. */
class RemoveGroup(private val group: NodeGroup) : GraphCommand {
    override val structural: Boolean get() = false
    override fun apply(graph: NodeGraph) {
        graph.removeGroup(group.id)
    }

    override fun revert(graph: NodeGraph) {
        if (!group.isAttached) graph.restoreGroup(group)
    }
}

/** Reverted in reverse order. */
class CompoundCommand(private val parts: List<GraphCommand>) : GraphCommand {
    override val structural: Boolean get() = parts.any { it.structural }
    override fun apply(graph: NodeGraph) = parts.forEach { it.apply(graph) }
    override fun revert(graph: NodeGraph) = parts.asReversed().forEach { it.revert(graph) }
}

/**
 * Undo/redo capped at [limit]. Use [execute], or [record] for an already-applied command (a drag on release).
 * [onChange] hears every apply, undo and redo.
 */
class GraphHistory(private val graph: NodeGraph, private val limit: Int = DEFAULT_LIMIT) {

    private val done = ArrayDeque<GraphCommand>()
    private val undone = ArrayDeque<GraphCommand>()

    var onChange: (structural: Boolean) -> Unit = {}

    val canUndo: Boolean get() = done.isNotEmpty()
    val canRedo: Boolean get() = undone.isNotEmpty()

    fun execute(command: GraphCommand) {
        command.apply(graph)
        record(command)
    }

    fun record(command: GraphCommand) {
        undone.clear()
        val merged = done.lastOrNull()?.merge(command)
        if (merged != null) {
            done.removeLast()
            done.addLast(merged)
        } else {
            done.addLast(command)
            while (done.size > limit) done.removeFirst()
        }
        onChange(command.structural)
    }

    fun undo(): Boolean {
        val command = done.removeLastOrNull() ?: return false
        command.revert(graph)
        undone.addLast(command)
        onChange(command.structural)
        return true
    }

    fun redo(): Boolean {
        val command = undone.removeLastOrNull() ?: return false
        command.apply(graph)
        done.addLast(command)
        onChange(command.structural)
        return true
    }

    /** For when the graph changed behind the history's back. */
    fun clear() {
        done.clear()
        undone.clear()
    }

    companion object {
        const val DEFAULT_LIMIT = 200
    }
}
