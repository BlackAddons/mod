package org.blackaddons.api.ui.node

import kotlin.math.abs

internal class NodePusher(private val graph: NodeGraph) {

    private class PushUnit(val ids: IntArray, val group: NodeGroup?)

    /**
     * Resolves overlaps as applied moves: groups push out foreign nodes, [pushers] push what they were
     * dropped on, and pushes cascade by the shortest way out. [fixed] never moves.
     */
    fun pushOut(fixed: Set<Int>, pushers: Collection<Int> = emptyList()): List<GraphCommand> {
        val moves = ArrayList<GraphCommand>()
        val budget = intArrayOf(PUSH_BUDGET)
        val box = FloatArray(4)
        for (id in pushers) {
            val node = graph.node(id) ?: continue
            box[0] = node.x; box[1] = node.y; box[2] = node.x + node.width; box[3] = node.y + node.height
            pushAway(box, graph.groupOf(id), setOf(id), fixed, moves, budget)
        }
        repeat(PUSH_PASSES) {
            val before = moves.size
            for (group in graph.groups().toList()) {
                if (graph.groupBounds(group, box)) pushAway(box, group, group.memberIds, fixed, moves, budget)
            }
            if (moves.size == before || budget[0] <= 0) return moves
        }
        return moves
    }

    /** Alone when it shares [besides]'s group or has none, else with its whole group. */
    private fun unitOf(id: Int, besides: NodeGroup?): PushUnit {
        val group = graph.groupOf(id)
        if (group == null || group === besides) return PushUnit(intArrayOf(id), null)
        return PushUnit(group.memberIds.filter { graph.node(it) != null }.toIntArray(), group)
    }

    private fun boxOf(unit: PushUnit, out: FloatArray): Boolean =
        (unit.group != null && graph.groupBounds(unit.group, out)) || graph.bounds(unit.ids.asList(), out)

    private fun overlaps(a: FloatArray, b: FloatArray) = a[0] < b[2] && a[2] > b[0] && a[1] < b[3] && a[3] > b[1]

    private fun pushAway(
        area: FloatArray, owner: NodeGroup?, own: Set<Int>, fixed: Set<Int>,
        moves: MutableList<GraphCommand>, budget: IntArray,
    ) = forEachOverlapping(area, owner, own, fixed, budget) { unit, other ->
        val left = area[0] - other[2] - PUSH_GAP
        val right = area[2] - other[0] + PUSH_GAP
        val up = area[1] - other[3] - PUSH_GAP
        val down = area[3] - other[1] + PUSH_GAP
        val px = if (-left < right) left else right
        val py = if (-up < down) up else down
        if (abs(px) < abs(py)) shove(unit, px, 0f, fixed, moves, budget) else shove(unit, 0f, py, fixed, moves, budget)
    }

    private fun shove(unit: PushUnit, dx: Float, dy: Float, fixed: Set<Int>, moves: MutableList<GraphCommand>, budget: IntArray) {
        moves += MoveNodes(unit.ids, dx, dy).also { it.apply(graph) }
        if (--budget[0] <= 0) return
        val area = FloatArray(4)
        if (!boxOf(unit, area)) return
        val owner = unit.group ?: graph.groupOf(unit.ids[0])
        forEachOverlapping(area, owner, unit.ids.toHashSet(), fixed, budget) { next, other ->
            when {
                dx > 0f -> shove(next, area[2] - other[0] + PUSH_GAP, 0f, fixed, moves, budget)
                dx < 0f -> shove(next, area[0] - other[2] - PUSH_GAP, 0f, fixed, moves, budget)
                dy > 0f -> shove(next, 0f, area[3] - other[1] + PUSH_GAP, fixed, moves, budget)
                else -> shove(next, 0f, area[1] - other[3] - PUSH_GAP, fixed, moves, budget)
            }
        }
    }

    private inline fun forEachOverlapping(
        area: FloatArray, owner: NodeGroup?, own: Set<Int>, fixed: Set<Int>, budget: IntArray,
        action: (PushUnit, FloatArray) -> Unit,
    ) {
        val hits = IntList()
        graph.nodeIndex.query(area[0], area[1], area[2], area[3], hits)
        val other = FloatArray(4)
        for (i in 0 until hits.size) {
            val id = hits[i]
            if (id in own || id in fixed || graph.node(id) == null) continue
            val unit = unitOf(id, owner)
            if (unit.ids.any { it in fixed || it in own }) continue
            if (!boxOf(unit, other) || !overlaps(area, other) || budget[0] <= 0) continue
            action(unit, other)
        }
    }

    private companion object {
        const val PUSH_PASSES = 8
        const val PUSH_BUDGET = 512
        const val PUSH_GAP = 16f
    }
}
