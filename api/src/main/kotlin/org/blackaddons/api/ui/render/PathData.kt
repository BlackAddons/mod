package org.blackaddons.api.ui.render

/**
 * CPU-side geometry recorder that [Renderer.createShape] turns into one reusable native shape - e.g. a
 * thousand wires drawn in one call. [clear] keeps storage, so rebuilding allocates nothing. Render thread
 * only.
 */
class PathData(expectedOps: Int = 16) {

    private var ops = ByteArray(expectedOps.coerceAtLeast(4))
    private var opCount = 0
    private var coords = FloatArray(expectedOps.coerceAtLeast(4) * 4)
    private var coordCount = 0

    val isEmpty: Boolean get() = opCount == 0

    /** Rough cost of building the shape. */
    val size: Int get() = opCount

    fun clear() {
        opCount = 0
        coordCount = 0
    }

    fun moveTo(x: Float, y: Float) = op(MOVE, 2).also { put(x); put(y) }

    fun lineTo(x: Float, y: Float) = op(LINE, 2).also { put(x); put(y) }

    fun cubicTo(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float) {
        op(CUBIC, 6)
        put(c1x); put(c1y); put(c2x); put(c2y); put(x); put(y)
    }

    fun close() = op(CLOSE, 0)

    /** Own contour. Filled is a box, stroked an outline. */
    fun rect(x: Float, y: Float, w: Float, h: Float) {
        op(RECT, 4)
        put(x); put(y); put(w); put(h)
    }

    /** Own contour; the low-detail wire. */
    fun segment(x1: Float, y1: Float, x2: Float, y2: Float) {
        moveTo(x1, y1)
        lineTo(x2, y2)
    }

    /** Closed polygon through interleaved `x, y` [points] (3+), rounded by [corner] - what [Render.polygon] draws. */
    fun polygon(points: FloatArray, corner: Float = 0f) {
        require(points.size >= 6 && points.size % 2 == 0) { "a polygon takes three x, y vertices or more, got ${points.size} floats" }
        op(POLYGON, points.size + 2)
        put(corner)
        put(points.size.toFloat())
        points.copyInto(coords, coordCount)
        coordCount += points.size
    }

    fun visit(visitor: Visitor) {
        var c = 0
        for (i in 0 until opCount) {
            when (ops[i]) {
                MOVE -> visitor.moveTo(coords[c], coords[c + 1]).also { c += 2 }
                LINE -> visitor.lineTo(coords[c], coords[c + 1]).also { c += 2 }
                CUBIC -> visitor.cubicTo(coords[c], coords[c + 1], coords[c + 2], coords[c + 3], coords[c + 4], coords[c + 5])
                    .also { c += 6 }
                CLOSE -> visitor.close()
                RECT -> visitor.rect(coords[c], coords[c + 1], coords[c + 2], coords[c + 3]).also { c += 4 }
                POLYGON -> {
                    val count = coords[c + 1].toInt()
                    visitor.polygon(coords.copyOfRange(c + 2, c + 2 + count), coords[c])
                    c += count + 2
                }
            }
        }
    }

    /** Implemented by renderers to build native shapes. */
    interface Visitor {
        fun moveTo(x: Float, y: Float)
        fun lineTo(x: Float, y: Float)
        fun cubicTo(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float)
        fun close()
        fun rect(x: Float, y: Float, w: Float, h: Float)
        fun polygon(points: FloatArray, corner: Float)
    }

    private fun op(code: Byte, coordsNeeded: Int) {
        if (opCount == ops.size) ops = ops.copyOf(ops.size * 2)
        if (coordCount + coordsNeeded > coords.size) coords = coords.copyOf(maxOf(coords.size * 2, coordCount + coordsNeeded))
        ops[opCount++] = code
    }

    private fun put(value: Float) {
        coords[coordCount++] = value
    }

    private companion object {
        const val MOVE: Byte = 0
        const val LINE: Byte = 1
        const val CUBIC: Byte = 2
        const val CLOSE: Byte = 3
        const val RECT: Byte = 4
        const val POLYGON: Byte = 5
    }
}

/** Pair [Renderer.createShape] with [Renderer.deleteShape]; a deleted shape draws nothing. */
class ShapePath
