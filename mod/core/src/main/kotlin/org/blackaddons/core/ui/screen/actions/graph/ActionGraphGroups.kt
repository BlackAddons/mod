package org.blackaddons.core.ui.screen.actions.graph

import org.blackaddons.api.ui.node.NodeGraph
import org.blackaddons.api.ui.node.NodeGroup
import org.blackaddons.core.actions.ActionGroup
import org.blackaddons.core.actions.ActionStorage
import java.util.IdentityHashMap

internal class ActionGraphGroups(private val graph: NodeGraph) {

    private var knownGroups: List<NodeGroup> = emptyList()
    private val removedAt = IdentityHashMap<ActionGroup, Int>()

    /** Triggers go in their owner's group, steps go with them. */
    fun load(groups: List<ActionGroup>) {
        val frames = groups.mapIndexed { i, group ->
            graph.addGroup(group.name, ActionGraphStyle.frameColor(group, i)).also {
                it.payload = group
                it.switchedOn = group.enabled
            }
        }
        for (node in graph.nodes()) {
            val ref = (node.payload as? TriggerPayload)?.ref ?: continue
            frames.firstOrNull { (it.payload as ActionGroup).id == ref.owner.groupId }?.let { graph.setGroup(node.id, it) }
        }
        deriveFollowers()
        knownGroups = graph.groups().toList()
        removedAt.clear()
    }

    /** New frames become groups, removed frames get deleted (undo restores), actions follow their trigger's group. */
    fun sync(groups: MutableList<ActionGroup>) {
        for (frame in knownGroups) {
            if (frame.isAttached) continue
            val group = frame.payload as? ActionGroup ?: continue
            val i = groups.indexOfFirst { it === group }
            if (i >= 0) removedAt[group] = i
            groups.removeIf { it === group }
        }
        val live = graph.groups().toList()
        for (frame in live) {
            val group = frame.payload as? ActionGroup ?: ActionGroup(name = frame.title).also {
                frame.payload = it
                frame.switchedOn = it.enabled
            }
            if (groups.none { it === group }) groups.add((removedAt.remove(group) ?: groups.size).coerceIn(0, groups.size), group)
        }
        knownGroups = live
        for (node in graph.nodes()) {
            val ref = (node.payload as? TriggerPayload)?.ref ?: continue
            ref.owner.groupId = (graph.groupOf(node.id)?.payload as? ActionGroup)?.id
        }
        deriveFollowers()
        ActionStorage.save()
    }

    fun restyle(frame: NodeGroup, groups: List<ActionGroup>) {
        val group = frame.payload as? ActionGroup ?: return
        frame.title = group.name
        frame.switchedOn = group.enabled
        frame.color = ActionGraphStyle.frameColor(group, groups.indexOfFirst { it === group }.coerceAtLeast(0))
    }

    fun followers(nodeId: Int): IntArray {
        val ref = (graph.node(nodeId)?.payload as? TriggerPayload)?.ref ?: return IntArray(0)
        val out = ArrayList<Int>()
        for (node in graph.nodes()) {
            val other = (node.payload as? TriggerPayload)?.ref ?: continue
            if (other.owner !== ref.owner) continue
            if (node.id != nodeId) out += node.id
            out += chainOf(node.id)
        }
        return out.toIntArray()
    }

    /** Steps follow their trigger. Orphan steps are ungrouped. */
    private fun deriveFollowers() {
        val placed = HashSet<Int>()
        for (node in graph.nodes()) {
            if (node.payload !is TriggerPayload) continue
            val group = graph.groupOf(node.id)
            for (id in chainOf(node.id)) {
                graph.setGroup(id, group)
                placed += id
            }
        }
        for (node in graph.nodes()) if (node.payload is StepPayload && node.id !in placed) graph.setGroup(node.id, null)
    }

    private fun chainOf(trigger: Int): List<Int> {
        val out = ArrayList<Int>()
        val stack = ArrayDeque(listOf(trigger))
        while (stack.isNotEmpty()) {
            val node = graph.node(stack.removeLast()) ?: continue
            for (slot in node.outputs.indices) {
                graph.linksAt(node.id, slot, output = true).forEach {
                    out += it.to
                    stack += it.to
                }
            }
        }
        return out
    }
}
