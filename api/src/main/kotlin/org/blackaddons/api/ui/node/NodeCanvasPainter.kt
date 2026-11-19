package org.blackaddons.api.ui.node

import org.blackaddons.api.ui.render.PathData
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.render.RenderLayer
import org.blackaddons.api.ui.render.ShapePath
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Palette
import org.blackaddons.api.ui.theme.Theme.withAlpha
import java.awt.Color
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.roundToInt

internal class NodeCanvasPainter(private val canvas: NodeCanvas) {

    private val graph get() = canvas.graph
    private val camera get() = canvas.camera
    private val drag get() = canvas.drag
    private val input get() = canvas.input

    private val style = NodeCanvasStyle()
    private val grid = NodeCanvasGrid(canvas.camera, style)
    private val body = NodeBodyPainter(canvas, style)
    private val wires = NodeWirePainter(canvas, style, body)
    val minimap = NodeCanvasMinimap(canvas, style)

    private val wireTiles = TiledShapes(emit = ::emitWire)
    private val nodeTiles = TiledShapes(emit = ::emitNode)

    val listener = object : GraphListener {
        override fun nodeAdded(node: GraphNode) = fileNode(node)
        override fun nodeMoved(node: GraphNode) = fileNode(node)
        override fun nodeStyled(node: GraphNode) = nodeTiles.invalidate(node.id)

        override fun nodeRemoved(node: GraphNode) {
            nodeTiles.remove(node.id)
            body.forget(node.id)
        }

        override fun linkAdded(link: GraphLink) = fileLink(link)
        override fun linkMoved(link: GraphLink) = fileLink(link)
        override fun linkRemoved(link: GraphLink) = wireTiles.remove(link.id)

        override fun cleared() {
            wireTiles.clear()
            nodeTiles.clear()
            body.clear()
            groupTitles.clear()
        }
    }

    private fun refreshStyle() {
        if (!style.refresh()) return
        grid.restyle()
        nodeTiles.invalidateAll()
    }

    fun fileAll() {
        graph.nodes().forEach(::fileNode)
        graph.links().forEach(::fileLink)
    }

    private fun fileNode(node: GraphNode) {
        if (drag.isMoving(node.id)) return
        nodeTiles.put(node.id, node.x, node.y, node.x + node.width, node.y + node.height)
    }

    private fun fileLink(link: GraphLink) {
        if (drag.active && (drag.isMoving(link.from) || drag.isMoving(link.to))) return
        val b = link.bounds
        wireTiles.put(link.id, b[0], b[1], b[2], b[3])
    }

    private fun emitWire(id: Int, mode: Int, out: TiledShapes.Buckets) {
        val link = graph.link(id) ?: return
        wires.append(link.curve, mode, out.of(style.wireColor(link.type)), 0f, 0f, 0f, 0f)
    }

    private fun emitNode(id: Int, mode: Int, out: TiledShapes.Buckets) {
        val node = graph.node(id) ?: return
        appendNodeBox(node, mode, out, 0f, 0f)
    }

    private fun appendNodeBox(node: GraphNode, mode: Int, out: TiledShapes.Buckets, dx: Float, dy: Float) {
        val x = node.x + dx
        val y = node.y + dy
        if (mode == MODE_OVERVIEW) {
            out.of(style.headerOf(node)).rect(x, y, node.width, node.height)
            return
        }
        out.of(if (node.dimmed) style.bodyDim else style.body).rect(x, y + NodeMetrics.HEADER, node.width, node.height - NodeMetrics.HEADER)
        out.of(style.headerOf(node)).rect(x, y, node.width, NodeMetrics.HEADER)
    }

    private val view = FloatArray(4)
    private val visible = IntList(256)

    fun paint() {
        refreshStyle()
        val x = canvas.boxX
        val y = canvas.boxY
        val w = canvas.boxW
        val h = canvas.boxH
        val zoom = camera.zoom.toFloat()
        val detail = canvas.detail

        Render.rect(x, y, w, h, style.background)
        Render.pushScissor(x, y, w, h)
        if (canvas.showGrid) grid.draw(x, y, w, h, zoom)

        camera.visibleWorld(view)
        val margin = VIEW_MARGIN / zoom
        view[0] -= margin; view[1] -= margin; view[2] += margin; view[3] += margin

        val dpu = canvas.devicePixels()
        Render.push()
        Render.translate(snap(x - camera.originX * zoom, dpu), snap(y - camera.originY * zoom, dpu))
        Render.scale(zoom, zoom)
        drawGroups(zoom, dpu)
        val showText = textShown()
        refreshScene(detail, zoom, showText)
        Render.layer(scene, region[0], region[1], region[2] - region[0], region[3] - region[1], drawScene)
        drawLive(detail, zoom, showText)
        wires.drawPending(zoom)
        Render.pop()

        drawMarquee(x, y)
        drawZoomLabel(x, y, h)
        Render.popScissor()
        minimap.draw(x, y, w, h)
    }

    fun textShown(): Boolean = NodeMetrics.TITLE_FONT * camera.zoom.toFloat() * canvas.devicePixels() >= MIN_TEXT_DEVICE_PX

    private fun snap(v: Double, dpu: Float): Float = if (dpu > 0f) ((v * dpu).roundToInt() / dpu.toDouble()).toFloat() else v.toFloat()

    private val scene = RenderLayer()
    private val region = FloatArray(4)
    private val sceneKey = IntArray(6) { Int.MIN_VALUE }
    private val sceneProbe = IntArray(6)
    private var scenePalette: Palette? = null
    private var sceneZoom = 1f
    private var sceneDetail = NodeDetail.FULL
    private var sceneShowText = true
    private var sceneGeneration = 0

    private var dragGeneration = 0

    private fun refreshScene(detail: NodeDetail, zoom: Float, showText: Boolean) {
        val key = sceneProbe
        key[0] = graph.visualVersion
        key[1] = graph.structureVersion
        key[2] = detail.ordinal
        key[3] = floor(ln(zoom.toDouble()) / ln(SCENE_ZOOM_STEP)).toInt()
        key[4] = dragGeneration
        key[5] = if (showText) 1 else 0
        val covered = view[0] >= region[0] && view[1] >= region[1] && view[2] <= region[2] && view[3] <= region[3]
        if (covered && key.contentEquals(sceneKey) && scenePalette === style.palette) return
        key.copyInto(sceneKey)
        scenePalette = style.palette
        sceneZoom = zoom
        sceneDetail = detail
        sceneShowText = showText
        val marginX = (view[2] - view[0]) * SCENE_MARGIN
        val marginY = (view[3] - view[1]) * SCENE_MARGIN
        region[0] = view[0] - marginX; region[1] = view[1] - marginY
        region[2] = view[2] + marginX; region[3] = view[3] + marginY
        sceneGeneration++
        Render.invalidateLayer(scene)
    }

    private val drawScene: () -> Unit = {
        val live = view.copyOf()
        region.copyInto(view)
        drawSceneWires(sceneDetail, sceneZoom)
        if (sceneDetail == NodeDetail.FULL || sceneDetail == NodeDetail.MEDIUM) {
            drawSceneNodes(sceneDetail, sceneShowText)
        } else {
            drawNodeTiles(sceneDetail)
        }
        live.copyInto(view)
    }

    private fun drawLive(detail: NodeDetail, zoom: Float, showText: Boolean) {
        val mode = if (detail == NodeDetail.OVERVIEW) NodeWirePainter.STRAIGHT else NodeWirePainter.CURVED
        val thickness = wires.width(detail, zoom)
        val socketScale = if (detail == NodeDetail.FULL && showText) 1f else NodeBodyPainter.SMALL_SOCKET
        val detailed = detail == NodeDetail.FULL || detail == NodeDetail.MEDIUM
        if (detailed) drawOutlines(zoom, socketScale)
        if (drag.active) wires.drawDragged(mode, thickness)
        else if (detailed) wires.drawHighlights(mode, thickness * HIGHLIGHT_WIDTH, socketScale, region, sceneGeneration)
        if (detailed) {
            body.drawHoveredField()
            if (detail == NodeDetail.FULL && showText) body.showCutOffText()
            body.drawHoveredSocket(detail)
            drawMovingNodes(detail, zoom, showText)
        } else {
            drawBatchedOverlays(detail, zoom)
        }
    }

    private val groupFrame = FloatArray(4)
    private val groupTints = HashMap<Color, Array<Color>>()
    private class GroupTitle(var source: String = "", var width: Float = -1f, var text: String = "")

    private val groupTitles = HashMap<NodeGroup, GroupTitle>()
    private val groupTitleHeight by lazy { Render.textHeight(NodeMetrics.GROUP_FONT, Fonts.medium) }
    private val isMoving: (Int) -> Boolean = { drag.isMoving(it) }

    private fun drawGroups(zoom: Float, dpu: Float) {
        val showTitle = NodeMetrics.GROUP_FONT * zoom * dpu >= MIN_TEXT_DEVICE_PX
        val f = groupFrame
        for (group in graph.groups()) {
            if (!graph.groupBounds(group, f, drag.dx, drag.dy, isMoving)) continue
            if (f[0] > view[2] || f[2] < view[0] || f[1] > view[3] || f[3] < view[1]) continue
            val w = f[2] - f[0]
            val h = f[3] - f[1]
            val r = NodeMetrics.GROUP_RADIUS
            val tint = groupTints.getOrPut(group.color) {
                arrayOf(group.color.withAlpha(GROUP_FILL_ALPHA), group.color.withAlpha(GROUP_HEADER_ALPHA), group.color.withAlpha(GROUP_BORDER_ALPHA))
            }
            Render.rect(f[0], f[1], w, h, tint[0], r)
            Render.halfRoundedRect(f[0], f[1], w, NodeMetrics.GROUP_HEADER, tint[1], r, roundTop = true)
            Render.hollowRect(f[0], f[1], w, h, 1f / zoom, tint[2], r)
            if (!showTitle) continue
            val switchedOn = group.switchedOn
            if (switchedOn != null) {
                Render.toggleSwitch(
                    NodeMetrics.groupToggleX(f), NodeMetrics.groupToggleY(f), NodeMetrics.TOGGLE_W, NodeMetrics.TOGGLE_H,
                    if (switchedOn) 1f else 0f, NodeBodyPainter.TOGGLE_ON, NodeBodyPainter.TOGGLE_OFF,
                )
            }
            val room = w - NodeMetrics.GROUP_PAD * 2f - if (switchedOn != null) NodeMetrics.TOGGLE_W + NodeMetrics.PAD else 0f
            val title = groupTitles.getOrPut(group) { GroupTitle() }
            if (title.source != group.title || title.width != room) {
                title.source = group.title
                title.width = room
                title.text = Render.ellipsize(group.title, room, NodeMetrics.GROUP_FONT, Fonts.medium)
            }
            Render.text(
                title.text, f[0] + NodeMetrics.GROUP_PAD, f[1] + (NodeMetrics.GROUP_HEADER - groupTitleHeight) / 2f,
                NodeMetrics.GROUP_FONT, style.textPrimary, Fonts.medium,
            )
        }
    }

    private val scratchPath = PathData(256)
    private val scratchBuckets = TiledShapes.Buckets()

    private fun drawSceneWires(detail: NodeDetail, zoom: Float) {
        wireTiles.mode = if (detail == NodeDetail.OVERVIEW) NodeWirePainter.STRAIGHT else NodeWirePainter.CURVED
        val thickness = wires.width(detail, zoom)
        wireTiles.draw(view) { shape, color -> Render.strokeShape(shape, thickness, color) }
    }

    private fun drawSceneNodes(detail: NodeDetail, showText: Boolean) {
        val full = detail == NodeDetail.FULL && showText
        visible.clear()
        graph.nodeIndex.query(view[0], view[1], view[2], view[3], visible)
        for (i in 0 until visible.size) {
            val id = visible[i]
            if (drag.isMoving(id)) continue
            val node = graph.node(id) ?: continue
            if (intersects(node, 0f, 0f)) body.drawNode(node, 0f, 0f, showText, full)
        }
    }

    private fun drawOutlines(zoom: Float, socketScale: Float) {
        val hover = input.hoverNode
        if (canvas.selection.isEmpty && hover < 0) return
        val selected = OUTLINE_PX / zoom
        if (canvas.selection.size <= OUTLINE_SCAN) {
            canvas.selection.forEach { id -> graph.node(id)?.let { outline(it, selected, style.accent, socketScale) } }
        } else {
            visible.clear()
            graph.nodeIndex.query(view[0], view[1], view[2], view[3], visible)
            for (i in 0 until visible.size) if (visible[i] in canvas.selection) graph.node(visible[i])?.let { outline(it, selected, style.accent, socketScale) }
        }
        if (hover >= 0 && hover !in canvas.selection) graph.node(hover)?.let { outline(it, 1f / zoom, style.border, socketScale) }
    }

    private fun outline(node: GraphNode, t: Float, color: Color, socketScale: Float) {
        if (drag.isMoving(node.id) || !intersects(node, 0f, 0f)) return
        val r = NodeMetrics.RADIUS
        Render.hollowRect(node.x - t, node.y - t, node.width + t * 2f, node.height + t * 2f, t, color, r + t)
        body.drawSockets(node, 0f, 0f, socketScale)
    }

    private fun drawMovingNodes(detail: NodeDetail, zoom: Float, showText: Boolean) {
        if (!drag.active) return
        val full = detail == NodeDetail.FULL && showText
        val t = OUTLINE_PX / zoom
        for (id in drag.movingIds) {
            val node = graph.node(id) ?: continue
            if (!intersects(node, drag.dx, drag.dy)) continue
            body.drawNode(node, drag.dx, drag.dy, showText, full)
            Render.hollowRect(
                node.x + drag.dx - t, node.y + drag.dy - t, node.width + t * 2f, node.height + t * 2f, t, style.accent,
                NodeMetrics.RADIUS + t,
            )
            body.drawSockets(node, drag.dx, drag.dy, if (full) 1f else NodeBodyPainter.SMALL_SOCKET)
        }
    }

    private fun intersects(node: GraphNode, dx: Float, dy: Float): Boolean =
        node.x + dx < view[2] && node.x + dx + node.width > view[0] && node.y + dy < view[3] && node.y + dy + node.height > view[1]

    private var outline: ShapePath? = null
    private val outlineKey = IntArray(4) { Int.MIN_VALUE }
    private val outlineProbe = IntArray(4)

    private var movingShapes: List<Pair<ShapePath, Color>> = emptyList()
    private var movingOutline: ShapePath? = null
    private var movingMode = -1

    private fun drawNodeTiles(detail: NodeDetail) {
        nodeTiles.mode = if (detail == NodeDetail.OVERVIEW) MODE_OVERVIEW else MODE_LOW
        Render.pushAntiAlias(false)
        nodeTiles.draw(view) { shape, color -> Render.fillShape(shape, color) }
        Render.popAntiAlias()
    }

    private fun drawBatchedOverlays(detail: NodeDetail, zoom: Float) {
        val mode = if (detail == NodeDetail.OVERVIEW) MODE_OVERVIEW else MODE_LOW
        val key = outlineProbe
        key[0] = canvas.selection.version; key[1] = graph.visualVersion; key[2] = graph.structureVersion
        key[3] = if (drag.active) 1 else 0
        if (!key.contentEquals(outlineKey)) {
            key.copyInto(outlineKey)
            outline?.let(Render::deleteShape)
            scratchPath.clear()
            canvas.selection.forEach { id ->
                if (drag.isMoving(id)) return@forEach
                graph.node(id)?.let { scratchPath.rect(it.x, it.y, it.width, it.height) }
            }
            outline = if (scratchPath.isEmpty) null else Render.createShape(scratchPath)
        }
        val thickness = OUTLINE_PX / zoom
        outline?.let { Render.strokeShape(it, thickness, style.accent) }

        if (!drag.active) return
        if (movingMode != mode) {
            releaseMoving()
            for (id in drag.movingIds) graph.node(id)?.let { appendNodeBox(it, mode, scratchBuckets, 0f, 0f) }
            movingShapes = scratchBuckets.take()
            scratchPath.clear()
            for (id in drag.movingIds) graph.node(id)?.let { scratchPath.rect(it.x, it.y, it.width, it.height) }
            movingOutline = if (scratchPath.isEmpty) null else Render.createShape(scratchPath)
            movingMode = mode
        }
        Render.push()
        Render.translate(drag.dx, drag.dy)
        Render.pushAntiAlias(false)
        for ((shape, color) in movingShapes) Render.fillShape(shape, color)
        Render.popAntiAlias()
        movingOutline?.let { Render.strokeShape(it, thickness, style.accent) }
        Render.pop()
    }

    private fun releaseMoving() {
        movingShapes.forEach { Render.deleteShape(it.first) }
        movingShapes = emptyList()
        movingOutline?.let(Render::deleteShape)
        movingOutline = null
        movingMode = -1
    }

    fun beginDrag() {
        dragGeneration++
        for (id in drag.movingIds) nodeTiles.remove(id)
        for (i in 0 until drag.rigidLinks.size) wireTiles.remove(drag.rigidLinks[i])
        for (i in 0 until drag.boundaryLinks.size) wireTiles.remove(drag.boundaryLinks[i])
        wires.beginDrag()
        movingMode = -1
    }

    fun endDrag(ids: IntArray) {
        dragGeneration++
        wires.endDrag()
        releaseMoving()
        for (id in ids) {
            val node = graph.node(id) ?: continue
            fileNode(node)
            graph.linksOf(id).forEach(::fileLink)
        }
    }

    private fun drawMarquee(x: Float, y: Float) {
        if (!input.marqueeActive) return
        val m = input.marquee
        val mx = x + min(m[0], m[2])
        val my = y + min(m[1], m[3])
        val mw = abs(m[2] - m[0])
        val mh = abs(m[3] - m[1])
        Render.rect(mx, my, mw, mh, style.accentFill)
        Render.hollowRect(mx, my, mw, mh, 1f, style.accentLine)
    }

    private var zoomLabel = ""
    private var zoomLabelPercent = -1

    private fun drawZoomLabel(x: Float, y: Float, h: Float) {
        val percent = (camera.zoom * 100).roundToInt()
        if (percent != zoomLabelPercent) {
            zoomLabelPercent = percent
            zoomLabel = "$percent%"
        }
        Render.text(zoomLabel, x + LABEL_MARGIN, y + h - LABEL_MARGIN - body.labelHeight, NodeMetrics.LABEL_FONT, style.textMuted, Fonts.regular)
    }

    fun release() {
        Render.deleteLayer(scene)
        sceneKey.fill(Int.MIN_VALUE)
        wireTiles.releaseShapes()
        nodeTiles.releaseShapes()
        grid.release()
        wires.release()
        outline?.let(Render::deleteShape)
        outline = null
        outlineKey.fill(Int.MIN_VALUE)
        releaseMoving()
        minimap.release()
    }

    companion object {
        const val LABEL_MARGIN = 8f
        private const val MODE_LOW = 0
        private const val MODE_OVERVIEW = 1
        private const val HIGHLIGHT_WIDTH = 1.25f
        private const val GROUP_FILL_ALPHA = 38
        private const val GROUP_HEADER_ALPHA = 120
        private const val GROUP_BORDER_ALPHA = 160
        private const val VIEW_MARGIN = 16f
        private const val MIN_TEXT_DEVICE_PX = 5f
        private const val SCENE_MARGIN = 0.35f
        private const val SCENE_ZOOM_STEP = 1.15
        private const val OUTLINE_SCAN = 64
        private const val OUTLINE_PX = 1.5f
    }
}
