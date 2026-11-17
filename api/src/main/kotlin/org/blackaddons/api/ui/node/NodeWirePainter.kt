package org.blackaddons.api.ui.node

import org.blackaddons.api.ui.render.PathData
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.render.ShapePath
import java.awt.Color
import kotlin.math.max

/** Live wires: highlighted, dragged and in hand. */
internal class NodeWirePainter(private val canvas: NodeCanvas, private val style: NodeCanvasStyle, private val body: NodeBodyPainter) {

    private val graph get() = canvas.graph
    private val drag get() = canvas.drag
    private val input get() = canvas.input

    private val curve = FloatArray(8)
    private val buckets = TiledShapes.Buckets()

    private var highlight: List<Pair<ShapePath, Color>> = emptyList()
    private val highlightLinks = IntList()
    private val highlightCandidates = IntList()
    private val highlightKey = IntArray(4) { Int.MIN_VALUE }
    private val highlightProbe = IntArray(4)

    private var rigidShapes: List<Pair<ShapePath, Color>> = emptyList()
    private var rigidMode = -1

    fun width(detail: NodeDetail, zoom: Float): Float = when (detail) {
        NodeDetail.FULL -> 2f * max(1f, zoom) / zoom
        NodeDetail.MEDIUM -> 1.6f / zoom
        NodeDetail.LOW, NodeDetail.OVERVIEW -> 0f
    }

    fun append(c: FloatArray, mode: Int, data: PathData, fromDx: Float, fromDy: Float, toDx: Float, toDy: Float) {
        if (fromDx == 0f && fromDy == 0f && toDx == 0f && toDy == 0f) {
            if (mode == STRAIGHT) {
                data.segment(c[0], c[1], c[6], c[7])
            } else {
                data.moveTo(c[0], c[1])
                data.cubicTo(c[2], c[3], c[4], c[5], c[6], c[7])
            }
            return
        }
        WireGeometry.controls(c[0] + fromDx, c[1] + fromDy, c[6] + toDx, c[7] + toDy, curve)
        append(curve, mode, data, 0f, 0f, 0f, 0f)
    }

    /* Only wires inside the scene region, so cost follows the screen, not the selection. */
    fun drawHighlights(mode: Int, thickness: Float, socketScale: Float, region: FloatArray, generation: Int) {
        val key = highlightProbe
        key[0] = canvas.selection.version; key[1] = input.hoverNode; key[2] = input.hoverLink; key[3] = generation
        if (!key.contentEquals(highlightKey)) {
            key.copyInto(highlightKey)
            highlight.forEach { Render.deleteShape(it.first) }
            highlight = buildHighlights(mode, region)
        }
        for ((shape, color) in highlight) Render.strokeShape(shape, thickness, color)
        for (i in 0 until highlightLinks.size) {
            val link = graph.link(highlightLinks[i]) ?: continue
            graph.node(link.from)?.let { body.drawSocketAt(it, link.fromSlot, output = true, 0f, 0f, socketScale) }
            graph.node(link.to)?.let { body.drawSocketAt(it, link.toSlot, output = false, 0f, 0f, socketScale) }
        }
    }

    private fun buildHighlights(mode: Int, region: FloatArray): List<Pair<ShapePath, Color>> {
        highlightLinks.clear()
        val selection = canvas.selection
        val hoverNode = input.hoverNode
        val hoverLink = input.hoverLink
        if (selection.isEmpty && hoverNode < 0 && hoverLink < 0) return emptyList()
        highlightCandidates.clear()
        graph.linkIndex.query(region[0], region[1], region[2], region[3], highlightCandidates)
        for (i in 0 until highlightCandidates.size) {
            val link = graph.link(highlightCandidates[i]) ?: continue
            val lit = link.id == hoverLink || link.from == hoverNode || link.to == hoverNode ||
                link.from in selection || link.to in selection
            if (!lit) continue
            highlightLinks.add(link.id)
            append(link.curve, mode, buckets.of(style.wireHighlight(link.type)), 0f, 0f, 0f, 0f)
        }
        return buckets.take()
    }

    fun drawDragged(mode: Int, thickness: Float) {
        if (rigidMode != mode) {
            rigidShapes.forEach { Render.deleteShape(it.first) }
            for (i in 0 until drag.rigidLinks.size) {
                val link = graph.link(drag.rigidLinks[i]) ?: continue
                append(link.curve, mode, buckets.of(style.wireColor(link.type)), 0f, 0f, 0f, 0f)
            }
            rigidShapes = buckets.take()
            rigidMode = mode
        }
        if (rigidShapes.isNotEmpty()) {
            Render.push()
            Render.translate(drag.dx, drag.dy)
            for ((shape, color) in rigidShapes) Render.strokeShape(shape, thickness, color)
            Render.pop()
        }
        if (drag.boundaryLinks.isEmpty) return
        val dx = drag.dx
        val dy = drag.dy
        for (i in 0 until drag.boundaryLinks.size) {
            val link = graph.link(drag.boundaryLinks[i]) ?: continue
            val fromMoves = drag.isMoving(link.from)
            val toMoves = drag.isMoving(link.to)
            append(
                link.curve, mode, buckets.of(style.wireHighlight(link.type)),
                if (fromMoves) dx else 0f, if (fromMoves) dy else 0f,
                if (toMoves) dx else 0f, if (toMoves) dy else 0f,
            )
        }
        drawOnce(buckets.take(), thickness)
    }

    fun drawPending(zoom: Float) {
        val anchor = input.wireAnchor ?: return
        val node = graph.node(anchor.node) ?: return
        val spec = (if (anchor.output) node.outputs else node.inputs).getOrNull(anchor.slot) ?: return
        val ax = if (anchor.output) NodeMetrics.outputX(node) else NodeMetrics.inputX(node)
        val ay = NodeMetrics.socketY(node, anchor.slot)
        var ex = input.wireEndX
        var ey = input.wireEndY
        input.wireTarget?.let { target ->
            val t = graph.node(target.node) ?: return@let
            ex = if (target.output) NodeMetrics.outputX(t) else NodeMetrics.inputX(t)
            ey = NodeMetrics.socketY(t, target.slot)
        }
        if (anchor.output) WireGeometry.controls(ax, ay, ex, ey, curve) else WireGeometry.controls(ex, ey, ax, ay, curve)
        val color = if (input.wireTarget != null) style.wireHighlight(spec.type) else style.wireColor(spec.type)
        append(curve, CURVED, buckets.of(color), 0f, 0f, 0f, 0f)
        drawOnce(buckets.take(), width(NodeDetail.FULL, zoom))
    }

    private fun drawOnce(shapes: List<Pair<ShapePath, Color>>, thickness: Float) {
        for ((shape, color) in shapes) {
            Render.strokeShape(shape, thickness, color)
            Render.deleteShape(shape)
        }
    }

    fun beginDrag() {
        rigidMode = -1
    }

    fun endDrag() {
        rigidShapes.forEach { Render.deleteShape(it.first) }
        rigidShapes = emptyList()
        rigidMode = -1
    }

    fun release() {
        highlight.forEach { Render.deleteShape(it.first) }
        highlight = emptyList()
        highlightKey.fill(Int.MIN_VALUE)
        endDrag()
    }

    companion object {
        const val CURVED = 0
        const val STRAIGHT = 1
    }
}
