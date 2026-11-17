package org.blackaddons.api.ui.node

import java.awt.Color

/** All methods default to no-ops. */
interface GraphListener {
    fun nodeAdded(node: GraphNode) {}
    fun nodeRemoved(node: GraphNode) {}

    /** Touching wires also get [linkMoved]. */
    fun nodeMoved(node: GraphNode) {}

    /** Looks, not position. */
    fun nodeStyled(node: GraphNode) {}
    fun linkAdded(link: GraphLink) {}
    fun linkRemoved(link: GraphLink) {}
    fun linkMoved(link: GraphLink) {}
    fun cleared() {}
}

/** Nodes and wires in [SpatialGrid]s. Ids aren't reused while the graph lives, so undo keeps them. UI thread only. */
class NodeGraph {

    private val nodes = ArrayList<GraphNode?>()
    private val links = ArrayList<GraphLink?>()
    private val listeners = ArrayList<GraphListener>()
    private val groups = ArrayList<NodeGroup?>()
    private val membership = HashMap<Int, NodeGroup>()

    val nodeIndex = SpatialGrid()
    val linkIndex = SpatialGrid()

    var nodeCount = 0
        private set
    var linkCount = 0
        private set

    /** Nodes or wires added/removed. */
    var structureVersion = 0
        private set

    /** Anything moved, resized or restyled. */
    var visualVersion = 0
        private set

    /** Group added, removed, renamed, recolored, or membership changed. */
    var groupVersion = 0
        private set

    fun addListener(listener: GraphListener) {
        listeners += listener
    }

    fun removeListener(listener: GraphListener) {
        listeners -= listener
    }

    fun node(id: Int): GraphNode? = nodes.getOrNull(id)

    fun link(id: Int): GraphLink? = links.getOrNull(id)

    fun nodes(): Sequence<GraphNode> = nodes.asSequence().filterNotNull()

    fun links(): Sequence<GraphLink> = links.asSequence().filterNotNull()

    /** Highest id + 1, for sizing per-id arrays. */
    val nodeIdCapacity: Int get() = nodes.size
    val linkIdCapacity: Int get() = links.size

    fun addNode(
        inputs: List<SocketSpec>,
        outputs: List<SocketSpec>,
        x: Float,
        y: Float,
        width: Float = NodeMetrics.DEFAULT_WIDTH,
        init: GraphNode.() -> Unit = {},
    ): GraphNode {
        val node = GraphNode(nodes.size, inputs, outputs, x, y, width)
        node.init()
        nodes += null
        attach(node)
        return node
    }

    /** Under its own id; wires are not restored. */
    fun restoreNode(node: GraphNode) {
        require(!node.isAttached) { "node ${node.id} is already in a graph" }
        require(nodes.getOrNull(node.id) == null) { "id ${node.id} is taken" }
        while (nodes.size <= node.id) nodes += null
        node.links.clear()
        attach(node)
    }

    private fun attach(node: GraphNode) {
        nodes[node.id] = node
        node.graph = this
        node.height = NodeMetrics.height(node)
        nodeCount++
        index(node)
        structureVersion++
        listeners.forEach { it.nodeAdded(node) }
    }

    /** Returns the node and its wires for undo. */
    fun removeNode(id: Int): Pair<GraphNode, List<LinkSpec>>? {
        val node = node(id) ?: return null
        val removed = ArrayList<LinkSpec>(node.links.size)
        while (!node.links.isEmpty) {
            val link = link(node.links[node.links.size - 1]) ?: break
            removed += link.spec
            disconnect(link.id)
        }
        nodes[id] = null
        node.graph = null
        nodeCount--
        nodeIndex.remove(id)
        structureVersion++
        listeners.forEach { it.nodeRemoved(node) }
        return node to removed
    }

    fun moveTo(id: Int, x: Float, y: Float) {
        val node = node(id) ?: return
        if (node.x == x && node.y == y) return
        node.x = x
        node.y = y
        geometryChanged(node)
    }

    fun moveBy(ids: IntArray, dx: Float, dy: Float) {
        if (dx == 0f && dy == 0f) return
        for (id in ids) {
            val node = node(id) ?: continue
            moveTo(id, node.x + dx, node.y + dy)
        }
    }

    /**
     * Whether a new wire into a single-wire input keeps the wires already there, for example where two branches join.
     */
    var joins: (LinkSpec) -> Boolean = { false }

    /** Graph rules only (sockets exist, types fit, no self-wire); host rules are the caller's. */
    fun canConnect(from: Int, fromSlot: Int, to: Int, toSlot: Int): Boolean {
        if (from == to) return false
        val out = node(from)?.outputs?.getOrNull(fromSlot) ?: return false
        val input = node(to)?.inputs?.getOrNull(toSlot) ?: return false
        return input.type.accepts(out.type)
    }

    /** A single-wire socket drops its old wire (for undo). Null if [canConnect] fails; the existing link if wired. */
    fun connect(from: Int, fromSlot: Int, to: Int, toSlot: Int): Pair<GraphLink, List<LinkSpec>>? {
        if (!canConnect(from, fromSlot, to, toSlot)) return null
        findLink(from, fromSlot, to, toSlot)?.let { return it to emptyList() }
        val fromNode = node(from)!!
        val toNode = node(to)!!
        val replaced = ArrayList<LinkSpec>(2)
        if (!fromNode.outputs[fromSlot].multiple) {
            linksAt(from, fromSlot, output = true).forEach { replaced += it.spec; disconnect(it.id) }
        }
        if (!toNode.inputs[toSlot].multiple && !joins(LinkSpec(from, fromSlot, to, toSlot))) {
            linksAt(to, toSlot, output = false).forEach { replaced += it.spec; disconnect(it.id) }
        }
        val link = GraphLink(links.size, from, fromSlot, to, toSlot, fromNode.outputs[fromSlot].type)
        links += link
        fromNode.links.add(link.id)
        toNode.links.add(link.id)
        linkCount++
        reshape(link)
        structureVersion++
        listeners.forEach { it.linkAdded(link) }
        return link to replaced
    }

    fun disconnect(linkId: Int): LinkSpec? {
        val link = link(linkId) ?: return null
        links[linkId] = null
        node(link.from)?.links?.removeValue(linkId)
        node(link.to)?.links?.removeValue(linkId)
        linkCount--
        linkIndex.remove(linkId)
        structureVersion++
        listeners.forEach { it.linkRemoved(link) }
        return link.spec
    }

    fun findLink(from: Int, fromSlot: Int, to: Int, toSlot: Int): GraphLink? {
        val node = node(from) ?: return null
        for (i in 0 until node.links.size) {
            val link = link(node.links[i]) ?: continue
            if (link.from == from && link.fromSlot == fromSlot && link.to == to && link.toSlot == toSlot) return link
        }
        return null
    }

    fun findLink(spec: LinkSpec): GraphLink? = findLink(spec.from, spec.fromSlot, spec.to, spec.toSlot)

    /** An output's when [output], else an input's. */
    fun linksAt(nodeId: Int, slot: Int, output: Boolean): List<GraphLink> {
        val node = node(nodeId) ?: return emptyList()
        val result = ArrayList<GraphLink>(1)
        for (i in 0 until node.links.size) {
            val link = link(node.links[i]) ?: continue
            if (output && link.from == nodeId && link.fromSlot == slot) result += link
            if (!output && link.to == nodeId && link.toSlot == slot) result += link
        }
        return result
    }

    fun linksOf(nodeId: Int): List<GraphLink> {
        val node = node(nodeId) ?: return emptyList()
        return (0 until node.links.size).mapNotNull { link(node.links[it]) }
    }

    /**
     * [remapInput] / [remapOutput] map old slots to new ones (null drops their wires). Returns dropped wires.
     */
    fun setSockets(
        nodeId: Int,
        inputs: List<SocketSpec>,
        outputs: List<SocketSpec>,
        remapInput: (Int) -> Int? = { it.takeIf { slot -> slot < inputs.size } },
        remapOutput: (Int) -> Int? = { it.takeIf { slot -> slot < outputs.size } },
    ): List<LinkSpec> {
        val node = node(nodeId) ?: return emptyList()
        val old = linksOf(nodeId).map { it.spec }
        old.forEach { findLink(it)?.let { link -> disconnect(link.id) } }
        node.inputs = inputs
        node.outputs = outputs
        geometryChanged(node)
        val dropped = ArrayList<LinkSpec>()
        for (spec in old) {
            val moved = if (spec.from == nodeId) {
                remapOutput(spec.fromSlot)?.let { spec.copy(fromSlot = it) }
            } else {
                remapInput(spec.toSlot)?.let { spec.copy(toSlot = it) }
            }
            val connected = moved?.let { connect(it.from, it.fromSlot, it.to, it.toSlot) }
            if (connected == null) dropped += spec else dropped += connected.second
        }
        return dropped
    }

    fun groups(): Sequence<NodeGroup> = groups.asSequence().filterNotNull()

    fun group(id: Int): NodeGroup? = groups.getOrNull(id)

    fun groupOf(nodeId: Int): NodeGroup? = membership[nodeId]

    /** Fill it with [setGroup]. */
    fun addGroup(title: String, color: Color): NodeGroup {
        val group = NodeGroup(groups.size, title, color)
        groups += group
        group.graph = this
        groupChanged()
        return group
    }

    /** With its old members. */
    fun restoreGroup(group: NodeGroup) {
        require(!group.isAttached) { "group ${group.id} is already in a graph" }
        while (groups.size <= group.id) groups += null
        groups[group.id] = group
        group.graph = this
        for (id in group.members) membership.put(id, group)?.takeIf { it !== group }?.members?.remove(id)
        groupChanged()
    }

    /** Nodes stay, ungrouped; the group keeps its member list for [restoreGroup]. */
    fun removeGroup(id: Int): NodeGroup? {
        val group = group(id) ?: return null
        groups[id] = null
        group.graph = null
        for (member in group.members) membership.remove(member, group)
        groupChanged()
        return group
    }

    /** Null ungroups. */
    fun setGroup(nodeId: Int, group: NodeGroup?) {
        val old = membership[nodeId]
        if (old === group) return
        old?.members?.remove(nodeId)
        if (group == null) {
            membership.remove(nodeId)
        } else {
            require(group.graph === this) { "group ${group.id} is not in this graph" }
            group.members += nodeId
            membership[nodeId] = group
        }
        groupChanged()
    }

    /** Padded, titled frame into [out]; [dx]/[dy] shift [shifted] mid-drag. False if no member is in the graph. */
    fun groupBounds(
        group: NodeGroup, out: FloatArray,
        dx: Float = 0f, dy: Float = 0f, shifted: (Int) -> Boolean = { false },
    ): Boolean {
        if (!bounds(group.members, out, dx, dy, shifted)) return false
        val pad = NodeMetrics.GROUP_PAD
        out[0] -= pad; out[1] -= pad + NodeMetrics.GROUP_HEADER
        out[2] += pad; out[3] += pad
        return true
    }

    /** False for an empty graph. */
    fun bounds(out: FloatArray): Boolean = bounds(nodes().map { it.id }.asIterable(), out)

    /** [dx]/[dy] shift the [shifted] nodes mid-drag. False, [out] untouched, if none is in the graph. */
    internal fun bounds(
        ids: Iterable<Int>, out: FloatArray,
        dx: Float = 0f, dy: Float = 0f, shifted: (Int) -> Boolean = { false },
    ): Boolean {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (id in ids) {
            val node = node(id) ?: continue
            val ox = if (shifted(id)) dx else 0f
            val oy = if (shifted(id)) dy else 0f
            minX = minOf(minX, node.x + ox); minY = minOf(minY, node.y + oy)
            maxX = maxOf(maxX, node.x + node.width + ox); maxY = maxOf(maxY, node.y + node.height + oy)
        }
        if (minX > maxX) return false
        out[0] = minX; out[1] = minY; out[2] = maxX; out[3] = maxY
        return true
    }

    fun clear() {
        nodes.forEach { it?.graph = null }
        nodes.clear()
        links.clear()
        nodeIndex.clear()
        linkIndex.clear()
        groups.forEach { it?.graph = null }
        groups.clear()
        membership.clear()
        groupVersion++
        nodeCount = 0
        linkCount = 0
        structureVersion++
        visualVersion++
        listeners.forEach { it.cleared() }
    }

    internal fun geometryChanged(node: GraphNode) {
        node.height = NodeMetrics.height(node)
        node.revision++
        visualVersion++
        index(node)
        listeners.forEach { it.nodeMoved(node) }
        for (i in 0 until node.links.size) link(node.links[i])?.let { link ->
            reshape(link)
            listeners.forEach { it.linkMoved(link) }
        }
    }

    internal fun groupChanged() {
        groupVersion++
    }

    internal fun styleChanged(node: GraphNode) {
        node.revision++
        visualVersion++
        listeners.forEach { it.nodeStyled(node) }
    }

    private fun index(node: GraphNode) =
        nodeIndex.put(node.id, node.x, node.y, node.x + node.width, node.y + node.height)

    private fun reshape(link: GraphLink) {
        val from = node(link.from) ?: return
        val to = node(link.to) ?: return
        WireGeometry.controls(
            NodeMetrics.outputX(from), NodeMetrics.socketY(from, link.fromSlot),
            NodeMetrics.inputX(to), NodeMetrics.socketY(to, link.toSlot),
            link.curve,
        )
        WireGeometry.bounds(link.curve, WIRE_MARGIN, link.bounds)
        linkIndex.put(link.id, link.bounds[0], link.bounds[1], link.bounds[2], link.bounds[3])
    }

    private companion object {
        const val WIRE_MARGIN = 6f
    }
}
