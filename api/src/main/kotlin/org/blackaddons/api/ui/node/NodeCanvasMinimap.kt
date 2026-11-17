package org.blackaddons.api.ui.node

import org.blackaddons.api.ui.layout.centered
import org.blackaddons.api.ui.render.PathData
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.render.ShapePath
import org.blackaddons.api.ui.theme.Theme
import kotlin.math.max
import kotlin.math.min

internal class NodeCanvasMinimap(private val canvas: NodeCanvas, private val style: NodeCanvasStyle) {

    private val graph get() = canvas.graph
    private val camera get() = canvas.camera

    private var shape: ShapePath? = null
    private var builtAt = 0L
    private val key = IntArray(2) { Int.MIN_VALUE }
    private val bounds = FloatArray(4)
    private val view = FloatArray(4)
    private val path = PathData(256)
    private var hasNodes = false
    private var shown = false
    private var mmX = 0f
    private var mmY = 0f
    private var mmW = 0f
    private var mmH = 0f
    private var mmScale = 1f
    private var mmOffX = 0f
    private var mmOffY = 0f

    fun draw(x: Float, y: Float, w: Float, h: Float) {
        shown = false
        if (!canvas.showMinimap || graph.nodeCount == 0 || w < MIN_VIEW || h < MIN_VIEW) return
        val now = System.currentTimeMillis()
        val stale = key[0] != graph.visualVersion || key[1] != graph.structureVersion
        if (shape == null || (stale && now - builtAt > REBUILD_MS)) {
            key[0] = graph.visualVersion
            key[1] = graph.structureVersion
            builtAt = now
            shape?.let(Render::deleteShape)
            hasNodes = graph.bounds(bounds)
            path.clear()
            graph.nodes().forEach { path.rect(it.x, it.y, it.width, it.height) }
            shape = if (path.isEmpty) null else Render.createShape(path)
        }
        val shape = shape ?: return
        if (!hasNodes) return

        mmW = min(WIDTH, w * 0.3f)
        mmH = mmW * ASPECT
        mmX = x + w - mmW - NodeCanvasPainter.LABEL_MARGIN
        mmY = y + h - mmH - NodeCanvasPainter.LABEL_MARGIN
        val b = bounds
        val bw = (b[2] - b[0]).coerceAtLeast(1f)
        val bh = (b[3] - b[1]).coerceAtLeast(1f)
        val inner = PAD * 2f
        mmScale = min((mmW - inner) / bw, (mmH - inner) / bh)
        mmOffX = centered(mmX, mmW, bw * mmScale) - b[0] * mmScale
        mmOffY = centered(mmY, mmH, bh * mmScale) - b[1] * mmScale
        shown = true

        Render.rect(mmX, mmY, mmW, mmH, style.minimapBack, Theme.RADIUS_SMALL)
        Render.hollowRect(mmX, mmY, mmW, mmH, 1f, style.border, Theme.RADIUS_SMALL)
        Render.pushScissor(mmX, mmY, mmW, mmH)
        Render.push()
        Render.translate(mmOffX, mmOffY)
        Render.scale(mmScale, mmScale)
        Render.pushAntiAlias(false)
        Render.fillShape(shape, style.minimapNodes)
        Render.popAntiAlias()
        Render.pop()
        camera.visibleWorld(view)
        val inset = 0.5f
        val vx0 = max(mmOffX + view[0] * mmScale, mmX + inset)
        val vy0 = max(mmOffY + view[1] * mmScale, mmY + inset)
        val vx1 = min(mmOffX + view[2] * mmScale, mmX + mmW - inset)
        val vy1 = min(mmOffY + view[3] * mmScale, mmY + mmH - inset)
        if (vx1 > vx0 && vy1 > vy0) Render.hollowRect(vx0, vy0, vx1 - vx0, vy1 - vy0, 1f, style.accent)
        Render.popScissor()
    }

    fun contains(px: Float, py: Float): Boolean =
        canvas.showMinimap && shown && px >= mmX && py >= mmY && px < mmX + mmW && py < mmY + mmH

    fun jump(px: Float, py: Float) {
        if (mmScale <= 0f) return
        camera.centerOn(((px - mmOffX) / mmScale).toDouble(), ((py - mmOffY) / mmScale).toDouble())
    }

    fun release() {
        shape?.let(Render::deleteShape)
        shape = null
    }

    private companion object {
        const val WIDTH = 160f
        const val ASPECT = 0.62f
        const val PAD = 6f
        const val MIN_VIEW = 240f
        const val REBUILD_MS = 250L
    }
}
