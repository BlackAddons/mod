package org.blackaddons.api.ui.theme

import kotlin.math.max
import kotlin.math.min

/**
 * Lifts the marks (arrows, flames, boxes, icons) off a vanilla GUI texture so they can sit on [Glass]: the panel
 * color, the bevel around it and every frame under a hole are cleared, gray marks become [dark] or [light] by whether
 * they are darker or lighter than the panel, and colored pixels keep their color.
 */
object GlassDecor {

    /**
     * [argb] is a [w] x [h] image; [holes] holds x, y pairs (slot centers) whose frames are cleared. Returns ARGB
     * pixels, or null when nothing is left to draw.
     */
    fun lift(argb: IntArray, w: Int, h: Int, holes: IntArray, dark: Int, light: Int): IntArray? {
        require(argb.size == w * h) { "Expected ${w * h} pixels, got ${argb.size}" }
        val panel = mostCommonOpaque(argb) ?: return null
        val cleared = clearFrames(argb, w, h, holes, panel)
        val panelLum = lum(panel)
        val out = IntArray(argb.size)
        var any = false
        for (i in argb.indices) {
            val c = argb[i]
            if (cleared[i] || c == panel || c ushr 24 == 0) continue
            val mark = mark(c, panelLum, dark, light)
            if (mark ushr 24 < MIN_ALPHA) continue
            out[i] = mark
            any = true
        }
        return if (any) out else null
    }

    /*
     * Flood from each hole through everything that is not the panel (slot frames), then from the image edge through
     * neutral pixels only (bevels), so an icon touching the bevel keeps its tinted outline.
     */
    private fun clearFrames(argb: IntArray, w: Int, h: Int, holes: IntArray, panel: Int): BooleanArray {
        val cleared = BooleanArray(argb.size)
        val stack = IntArray(argb.size)
        var top = 0
        var bevel = false
        fun push(x: Int, y: Int, seed: Boolean = false) {
            if (x !in 0 until w || y !in 0 until h) return
            val i = y * w + x
            if (cleared[i] || argb[i] == panel || bevel && !seed && !neutral(argb[i], panel)) return
            cleared[i] = true
            stack[top++] = i
        }
        fun flood() {
            while (top > 0) {
                val i = stack[--top]
                val x = i % w
                val y = i / w
                push(x + 1, y)
                push(x - 1, y)
                push(x, y + 1)
                push(x, y - 1)
            }
        }
        for (k in 0 until holes.size / 2) push(holes[k * 2], holes[k * 2 + 1])
        flood()
        bevel = true
        for (x in 0 until w) {
            push(x, 0, seed = true)
            push(x, h - 1, seed = true)
        }
        for (y in 0 until h) {
            push(0, y, seed = true)
            push(w - 1, y, seed = true)
        }
        flood()
        return cleared
    }

    private fun neutral(c: Int, panel: Int): Boolean {
        if (c ushr 24 == 0) return true
        return spread(c, 0) <= NEUTRAL || spread(c, panel) <= NEUTRAL
    }

    private fun spread(c: Int, base: Int): Int {
        val r = ((c shr 16) and 0xFF) - ((base shr 16) and 0xFF)
        val g = ((c shr 8) and 0xFF) - ((base shr 8) and 0xFF)
        val b = (c and 0xFF) - (base and 0xFF)
        return max(r, max(g, b)) - min(r, min(g, b))
    }

    private fun mark(c: Int, panelLum: Float, dark: Int, light: Int): Int {
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        if (max(r, max(g, b)) - min(r, min(g, b)) > COLORED) return c
        val a = (c ushr 24) / 255f
        val d = lum(c) - panelLum
        val (color, strength) = if (d < 0) dark to -d / max(panelLum, EPS) else light to d / max(1f - panelLum, EPS)
        val alpha = ((color ushr 24) * a * min(1f, strength * GAIN)).toInt()
        return (alpha shl 24) or (color and 0xFFFFFF)
    }

    private fun mostCommonOpaque(argb: IntArray): Int? {
        val counts = HashMap<Int, Int>()
        for (c in argb) if (c ushr 24 == 0xFF) counts.merge(c, 1, Int::plus)
        return counts.maxByOrNull { it.value }?.key
    }

    private fun lum(c: Int): Float =
        (0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)) / 255f

    private const val COLORED = 40
    private const val NEUTRAL = 8
    private const val GAIN = 1.5f
    private const val MIN_ALPHA = 8
    private const val EPS = 0.01f
}
