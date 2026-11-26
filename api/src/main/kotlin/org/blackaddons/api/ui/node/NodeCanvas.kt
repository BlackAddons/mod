package org.blackaddons.api.ui.node

import org.blackaddons.api.ui.layout.Rect
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.widget.*
import org.blackaddons.api.util.FrameClock
import java.awt.Color
import kotlin.math.max

data class SocketRef(val node: Int, val slot: Int, val output: Boolean)

/** Context-menu location; all null on empty canvas. */
class CanvasPoint(val node: GraphNode?, val link: GraphLink?, val worldX: Float, val worldY: Float, val group: NodeGroup? = null)

/**
 * Pannable, zoomable node editor whose per-frame cost follows what's on screen: [SpatialGrid] lookups,
 * [TiledShapes] at low zoom, [NodeLod] dropping unreadable detail, drags as one cached layer.
 *
 * Mouse: wheel zooms, middle/right-drag pans, left-drag box-selects (Shift add, Ctrl remove) or moves
 * nodes (Shift snaps), socket drag wires, Ctrl-click cuts a wire, right-click opens [onContextMenu].
 * Dropping nodes into a group's frame adds them; groups and dropped nodes push others away.
 * Keys: Delete/Backspace/X, Ctrl+Z / Ctrl+Y (Ctrl+Shift+Z), Ctrl+A, Home frames all, F frames selection,
 * arrows nudge. Every edit goes through [history].
 */
class NodeCanvas(val graph: NodeGraph) : Widget {

    val camera = NodeCamera()
    val history = GraphHistory(graph)
    val selection = NodeSelection()

    private val lod = NodeLod()
    val detail: NodeDetail get() = lod.detail

    /** Host wiring rules on top of socket types (e.g. no cycles). */
    var connectionFilter: (LinkSpec) -> Boolean = { true }

    var onActivate: (GraphNode) -> Unit = {}

    /** Also on undo/redo; the host rebuilds its rows and saves. */
    var onPropertyEdited: (GraphNode) -> Unit = {}

    var onToggle: (GraphNode) -> Unit = {}

    var onGroupToggle: (NodeGroup) -> Unit = {}

    /** Right-click without a drag. */
    var onContextMenu: (CanvasPoint) -> Unit = {}

    /** Nodes that follow [nodeId] between groups (a host's steps following their trigger). */
    var groupFollowers: (nodeId: Int) -> IntArray = { IntArray(0) }

    /** After every edit, undo and redo; [structural] when nodes or wires came or went. */
    var onEdited: (structural: Boolean) -> Unit = {}

    var showGrid = true
    var showMinimap = true

    /** For text legibility; null reads [UiManager.pixelScale]. */
    var devicePixelScale: Float? = null

    override var width: Float = 0f
    override var height: Float = 0f
    override var focused: Boolean = false

    internal val box = Rect()
    internal var boxX = 0f
    internal var boxY = 0f
    internal var boxW = 0f
    internal var boxH = 0f

    internal val drag = NodeDrag(this)
    internal val pusher = NodePusher(graph)
    internal val properties = NodePropertyEditor(this)
    internal val input = NodeCanvasInput(this)
    internal val painter = NodeCanvasPainter(this)
    internal val minimap get() = painter.minimap

    private val frameClock = FrameClock()
    private var framedOnce = false

    var frameOnFirstDraw = true

    private val scratch = IntList(64)
    private val frame = FloatArray(4)

    init {
        history.onChange = { structural ->
            selection.retainExisting(graph)
            onEdited(structural)
        }
        graph.addListener(painter.listener)
        painter.fileAll()
    }

    override fun draw(x: Float, y: Float) = render(x, y, width, height)

    /** In the panel's units. */
    fun render(x: Float, y: Float, w: Float, h: Float) {
        width = w
        height = h
        boxX = x; boxY = y; boxW = w; boxH = h
        box.set(x, y, w, h)
        camera.viewportWidth = w
        camera.viewportHeight = h
        if (!framedOnce && w > 0f && h > 0f) {
            framedOnce = true
            if (frameOnFirstDraw) frameAll(smooth = false)
        }

        camera.update(frameClock.tick())
        lod.update(camera.zoom)
        input.refreshHover()
        painter.paint()
        properties.draw()
    }

    /** Text field suggestions; call after the rest of the screen. */
    fun renderOverlay(mouseX: Float, mouseY: Float) = properties.renderOverlay(mouseX, mouseY)

    internal val propertiesShown: Boolean get() = lod.detail == NodeDetail.FULL && painter.textShown()

    internal fun devicePixels(): Float = devicePixelScale ?: UiManager.pixelScale

    /** Call when the screen closes. Native shapes are rebuilt if drawn again. */
    fun release() {
        cancelGesture()
        painter.release()
    }

    fun frameAll(smooth: Boolean = true) {
        if (graph.bounds(frame)) camera.frame(frame[0], frame[1], frame[2], frame[3], FRAME_PADDING, smooth)
    }

    fun frameSelection(smooth: Boolean = true) {
        if (selection.isEmpty) return frameAll(smooth)
        if (graph.bounds(selection.toIntArray().asList(), frame)) camera.frame(frame[0], frame[1], frame[2], frame[3], FRAME_PADDING, smooth)
    }

    /** One undo step. */
    fun deleteSelection() {
        val ids = selection.toIntArray().filter { graph.node(it)?.deletable == true }.toIntArray()
        if (ids.isEmpty()) return
        history.execute(RemoveNodes(ids))
    }

    /** One undo step; pushes other nodes out of the new frame. Null with nothing selected. */
    fun groupSelection(title: String, color: Color): NodeGroup? {
        val ids = withFollowers(selection.toIntArray().filter { graph.node(it) != null })
        if (ids.isEmpty()) return null
        val add = AddGroup(title, color, ids).also { it.apply(graph) }
        record(listOf(add) + pusher.pushOut(ids.toHashSet()))
        return add.created
    }

    fun ungroup(group: NodeGroup) = history.execute(RemoveGroup(group))

    /** Null removes them from groups; pushes out what the change uncovers. */
    fun setGroup(ids: IntArray, group: NodeGroup?) {
        val all = withFollowers(ids.asList())
        val set = SetGroup(all, group).also { it.apply(graph) }
        record(listOf(set) + pusher.pushOut(all.toHashSet()))
    }

    /** One undo step. */
    fun moveNodesTo(targets: Map<Int, FloatArray>) {
        val moves = targets.mapNotNull { (id, at) ->
            val node = graph.node(id) ?: return@mapNotNull null
            MoveNodes(intArrayOf(id), at[0] - node.x, at[1] - node.y).also { it.apply(graph) }
        }
        if (moves.isEmpty()) return
        record(moves + pusher.pushOut(emptySet(), pushers = targets.keys))
    }

    internal fun withFollowers(ids: List<Int>): IntArray {
        val all = LinkedHashSet(ids)
        for (id in ids) groupFollowers(id).forEach { all += it }
        return all.toIntArray()
    }

    /** Without an undo step - for a fresh layout. */
    fun separateGroups() {
        pusher.pushOut(emptySet())
    }

    /** Innermost group frame at the point, ignoring nodes. */
    fun groupAt(wx: Float, wy: Float): NodeGroup? {
        var best: NodeGroup? = null
        var bestArea = Float.MAX_VALUE
        for (group in graph.groups()) {
            if (!graph.groupBounds(group, frame)) continue
            if (wx < frame[0] || wy < frame[1] || wx >= frame[2] || wy >= frame[3]) continue
            val area = (frame[2] - frame[0]) * (frame[3] - frame[1])
            if (area < bestArea) {
                best = group
                bestArea = area
            }
        }
        return best
    }

    internal fun record(parts: List<GraphCommand>) {
        history.record(parts.singleOrNull() ?: CompoundCommand(parts))
    }

    fun selectAll() {
        graph.nodes().forEach { selection.add(it.id) }
    }

    /** Add nodes here, not on the graph, or undo won't know about them. */
    fun addNode(
        inputs: List<SocketSpec>,
        outputs: List<SocketSpec>,
        x: Float,
        y: Float,
        width: Float = NodeMetrics.DEFAULT_WIDTH,
        init: GraphNode.() -> Unit = {},
    ): GraphNode {
        val node = graph.addNode(inputs, outputs, x, y, width, init)
        history.record(AddNode(node))
        selection.set(node.id)
        return node
    }

    fun disconnect(link: GraphLink) = history.execute(Disconnect(link.spec))

    fun canConnect(spec: LinkSpec): Boolean =
        graph.canConnect(spec.from, spec.fromSlot, spec.to, spec.toSlot) && connectionFilter(spec)

    internal fun worldX(panelX: Float): Double = camera.toWorldX(panelX - boxX)
    internal fun worldY(panelY: Float): Double = camera.toWorldY(panelY - boxY)

    /** Selected nodes draw on top, so they win. */
    fun nodeAt(wx: Float, wy: Float): GraphNode? {
        scratch.clear()
        graph.nodeIndex.query(wx, wy, wx, wy, scratch)
        var best: GraphNode? = null
        var bestRank = Long.MIN_VALUE
        for (i in 0 until scratch.size) {
            val node = graph.node(scratch[i]) ?: continue
            if (!node.contains(wx, wy)) continue
            val rank = (if (node.id in selection) 1L shl 40 else 0L) + node.id
            if (rank > bestRank) {
                best = node
                bestRank = rank
            }
        }
        return best
    }

    /** Grab radius stays usable when zoomed out. */
    fun socketAt(wx: Float, wy: Float, accept: ((SocketRef) -> Boolean)? = null): SocketRef? {
        if (lod.detail.ordinal >= NodeDetail.LOW.ordinal) return null
        val grab = max(NodeMetrics.SOCKET_RADIUS + 2f, NodeMetrics.SOCKET_GRAB / camera.zoom.toFloat())
        scratch.clear()
        graph.nodeIndex.query(wx - grab, wy - grab, wx + grab, wy + grab, scratch)
        var best: SocketRef? = null
        var bestDist = grab * grab
        for (i in 0 until scratch.size) {
            val node = graph.node(scratch[i]) ?: continue
            for (slot in node.inputs.indices) {
                val d = dist2(wx, wy, NodeMetrics.inputX(node), NodeMetrics.socketY(node, slot))
                if (d <= bestDist) {
                    val ref = SocketRef(node.id, slot, output = false)
                    if (accept == null || accept(ref)) {
                        best = ref; bestDist = d
                    }
                }
            }
            for (slot in node.outputs.indices) {
                val d = dist2(wx, wy, NodeMetrics.outputX(node), NodeMetrics.socketY(node, slot))
                if (d <= bestDist) {
                    val ref = SocketRef(node.id, slot, output = true)
                    if (accept == null || accept(ref)) {
                        best = ref; bestDist = d
                    }
                }
            }
        }
        return best
    }

    fun linkAt(wx: Float, wy: Float): GraphLink? {
        if (lod.detail == NodeDetail.OVERVIEW) return null
        val tolerance = LINK_GRAB / camera.zoom.toFloat()
        scratch.clear()
        graph.linkIndex.query(wx - tolerance, wy - tolerance, wx + tolerance, wy + tolerance, scratch)
        var best: GraphLink? = null
        var bestDist = tolerance * tolerance
        for (i in 0 until scratch.size) {
            val link = graph.link(scratch[i]) ?: continue
            val b = link.bounds
            if (wx < b[0] || wx > b[2] || wy < b[1] || wy > b[3]) continue
            val d = WireGeometry.distanceSq(link.curve, wx, wy)
            if (d <= bestDist) {
                best = link
                bestDist = d
            }
        }
        return best
    }

    override fun mouseClicked(mouse: MouseButtonInput): Boolean = input.mouseClicked(mouse)

    override fun mouseDragged(drag: MouseDragInput): Boolean =
        input.mouseDragged(drag)

    override fun mouseReleased(mouse: MouseButtonInput): Boolean = input.mouseReleased(mouse)

    override fun mouseScrolled(wheel: MouseScrollInput): Boolean = input.mouseScrolled(wheel)

    override fun keyPressed(key: KeyInput): Boolean = input.keyPressed(key)

    override fun charTyped(typed: CharInput): Boolean = properties.charTyped(typed)

    /** Abandons a gesture unrecorded. Call before reading the graph on close, so a held wire isn't saved as cut. */
    fun cancelGesture() = input.cancelGesture()

    private fun dist2(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx
        val dy = ay - by
        return dx * dx + dy * dy
    }

    private companion object {
        const val FRAME_PADDING = 40f
        const val LINK_GRAB = 5f
    }
}
