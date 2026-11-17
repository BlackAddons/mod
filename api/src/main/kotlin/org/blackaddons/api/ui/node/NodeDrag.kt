package org.blackaddons.api.ui.node

import kotlin.math.abs

internal class NodeDrag(private val canvas: NodeCanvas) {

    private val graph get() = canvas.graph

    var active = false
        private set
    var dx = 0f
    var dy = 0f
    var movingIds = IntArray(0)
        private set
    private var movingFlags = BooleanArray(64)
    val rigidLinks = IntList()
    val boundaryLinks = IntList()

    fun isMoving(id: Int): Boolean = active && isFlagged(id)

    private fun isFlagged(id: Int) = id < movingFlags.size && movingFlags[id]

    fun begin(ids: IntArray) {
        movingIds = ids
        if (movingIds.isEmpty()) return
        val top = movingIds.max()
        if (movingFlags.size <= top) movingFlags = BooleanArray(maxOf(top + 1, movingFlags.size * 2))
        movingIds.forEach { movingFlags[it] = true }
        rigidLinks.clear()
        boundaryLinks.clear()
        val seen = HashSet<Int>()
        for (id in movingIds) {
            for (link in graph.linksOf(id)) {
                if (!seen.add(link.id)) continue
                val both = isFlagged(link.from) && isFlagged(link.to)
                if (both) rigidLinks.add(link.id) else boundaryLinks.add(link.id)
            }
        }
        dx = 0f
        dy = 0f
        active = true
        canvas.painter.beginDrag()
    }

    fun end(commit: Boolean) {
        val ids = movingIds
        val movedX = dx
        val movedY = dy
        active = false
        ids.forEach { if (it < movingFlags.size) movingFlags[it] = false }
        canvas.painter.endDrag(ids)
        movingIds = IntArray(0)
        rigidLinks.clear()
        boundaryLinks.clear()
        if (commit && (abs(movedX) > 0f || abs(movedY) > 0f)) drop(ids, movedX, movedY)
    }

    /**
     * Dropped nodes join the group whose pre-drag frame they landed in (their own first) or leave their
     * group. A group dragged whole stays itself. Then groups push out foreign nodes.
     */
    private fun drop(ids: IntArray, dx: Float, dy: Float) {
        val moving = ids.toHashSet()
        val frames = graph.groups().mapNotNull { group ->
            val f = FloatArray(4)
            val carried = group.memberIds.all { it in moving || graph.node(it) == null }
            if (!carried && graph.groupBounds(group, f)) group to f else null
        }.toList()
        val parts = ArrayList<GraphCommand>()
        parts += MoveNodes(ids, dx, dy).also { it.apply(graph) }
        val joins = LinkedHashMap<NodeGroup?, IntList>()
        for (id in ids) {
            val node = graph.node(id) ?: continue
            val current = graph.groupOf(id)
            if (current != null && frames.none { it.first === current }) continue
            val holding = frames.filter { (_, f) -> node.centerX >= f[0] && node.centerY >= f[1] && node.centerX < f[2] && node.centerY < f[3] }
            val target = holding.firstOrNull { it.first === current }?.first
                ?: holding.minByOrNull { (_, f) -> (f[2] - f[0]) * (f[3] - f[1]) }?.first
            if (target !== current) joins.getOrPut(target) { IntList() }.add(id)
        }
        for ((target, list) in joins) {
            parts += SetGroup(canvas.withFollowers(List(list.size) { list[it] }), target).also { it.apply(graph) }
        }
        parts += canvas.pusher.pushOut(moving, pushers = moving)
        canvas.record(parts)
    }
}
