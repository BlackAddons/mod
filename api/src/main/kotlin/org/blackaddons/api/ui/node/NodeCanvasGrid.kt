package org.blackaddons.api.ui.node

import org.blackaddons.api.ui.render.PathData
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.render.ShapePath
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.util.packInts
import java.awt.Color
import kotlin.math.ceil
import kotlin.math.roundToInt

internal class NodeCanvasGrid(private val camera: NodeCamera, private val style: NodeCanvasStyle) {

    private val colors = arrayOfNulls<Color>(LEVELS.size * ALPHA_STEPS)
    private val patterns = LinkedHashMap<Long, ShapePath>()
    private val path = PathData(256)

    fun restyle() = colors.fill(null)

    fun draw(x: Float, y: Float, w: Float, h: Float, zoom: Float) {
        for (level in LEVELS.indices) {
            val px = LEVELS[level] * zoom
            if (px < FADE_START) continue
            val alpha = smoothstep(FADE_START, FADE_END, px)
            val color = color(level, alpha)
            if (color.alpha == 0) continue
            val cols = bucket(ceil(w / px).toInt() + 2)
            val rows = bucket(ceil(h / px).toInt() + 2)
            if (cols > MAX_LINES || rows > MAX_LINES) continue
            val pattern = pattern(cols, rows)
            val offX = -(camera.originX * zoom).mod(px.toDouble()).toFloat()
            val offY = -(camera.originY * zoom).mod(px.toDouble()).toFloat()
            Render.push()
            Render.translate(x + offX, y + offY)
            Render.scale(px, px)
            Render.strokeShape(pattern, 0, color)
            Render.pop()
        }
    }

    private fun color(level: Int, alpha: Float): Color {
        val step = (alpha.coerceIn(0f, 1f) * (ALPHA_STEPS - 1)).roundToInt()
        val index = level * ALPHA_STEPS + step
        return colors[index] ?: style.textMuted.withAlpha(BASE_ALPHA[level] * step / (ALPHA_STEPS - 1))
            .also { colors[index] = it }
    }

    private fun pattern(cols: Int, rows: Int): ShapePath {
        val key = packInts(cols, rows)
        patterns.remove(key)?.let { patterns[key] = it; return it }
        path.clear()
        for (c in 0..cols) path.segment(c.toFloat(), 0f, c.toFloat(), rows.toFloat())
        for (r in 0..rows) path.segment(0f, r.toFloat(), cols.toFloat(), r.toFloat())
        val shape = Render.createShape(path)
        patterns[key] = shape
        while (patterns.size > CACHE) {
            val eldest = patterns.keys.first()
            patterns.remove(eldest)?.let(Render::deleteShape)
        }
        return shape
    }

    private fun bucket(n: Int): Int = (n + 7) / 8 * 8

    fun release() {
        patterns.values.forEach(Render::deleteShape)
        patterns.clear()
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private companion object {
        val LEVELS = floatArrayOf(NodeMetrics.GRID, NodeMetrics.GRID * 5f, NodeMetrics.GRID * 25f)
        val BASE_ALPHA = intArrayOf(26, 40, 56)
        const val ALPHA_STEPS = 16
        const val FADE_START = 6f
        const val FADE_END = 24f
        const val MAX_LINES = 512
        const val CACHE = 6
    }
}
