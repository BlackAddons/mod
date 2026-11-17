package org.blackaddons.api.ui.render

import org.joml.Matrix3x2f
import org.joml.Matrix3x2fc
import org.joml.Vector2f

/**
 * CPU mirror of the renderer's transform stack, since the backend doesn't expose it. Every matrix op must be
 * fed here; [reset] per pass. Used by `Render.chromaText`.
 */
class TransformTracker {
    private val stack = ArrayDeque<Matrix3x2f>()
    private val current = Matrix3x2f()
    private val scratch = Vector2f()

    fun reset() {
        stack.clear()
        current.identity()
    }

    fun push() { stack.addLast(Matrix3x2f(current)) }
    fun pop() {
        val restored = stack.removeLastOrNull()
        if (restored != null) current.set(restored) else current.identity()
    }

    fun translate(x: Float, y: Float) { current.translate(x, y) }
    fun scale(x: Float, y: Float) { current.scale(x, y) }
    fun rotate(radians: Float) { current.rotate(radians) }
    fun mul(matrix: Matrix3x2fc) { current.mul(matrix) }

    fun toScreen(x: Float, y: Float): FloatArray {
        current.transformPosition(scratch.set(x, y))
        return floatArrayOf(scratch.x, scratch.y)
    }
}
