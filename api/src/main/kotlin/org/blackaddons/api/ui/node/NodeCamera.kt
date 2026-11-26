package org.blackaddons.api.ui.node

import org.blackaddons.api.ui.node.NodeCamera.Companion.STEP
import org.blackaddons.api.util.expSmoothing
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow

/**
 * `screen = (world - origin) * zoom`. Wheel zoom keeps the point under the cursor fixed and eases in log
 * space; released pans coast. [update] advances motion per frame. Doubles, since a float origin loses
 * precision far out.
 */
class NodeCamera {

    var originX = 0.0
        private set
    var originY = 0.0
        private set
    var zoom = 1.0
        private set

    var minZoom = DEFAULT_MIN_ZOOM
        set(value) {
            field = value
            zoom = clampZoom(zoom)
            targetZoom = clampZoom(targetZoom)
        }
    var maxZoom = DEFAULT_MAX_ZOOM
        set(value) {
            field = value
            zoom = clampZoom(zoom)
            targetZoom = clampZoom(targetZoom)
        }

    /** Set by the canvas every frame. */
    var viewportWidth = 0f
    var viewportHeight = 0f

    private var targetZoom = 1.0

    private var anchorScreenX = 0.0
    private var anchorScreenY = 0.0
    private var anchorWorldX = 0.0
    private var anchorWorldY = 0.0

    private var flying = false
    private var flyCenterX = 0.0
    private var flyCenterY = 0.0

    private var velocityX = 0.0
    private var velocityY = 0.0

    val isZooming: Boolean get() = abs(ln(targetZoom / zoom)) > ZOOM_EPSILON
    val isMoving: Boolean get() = isZooming || flying || velocityX != 0.0 || velocityY != 0.0

    fun toWorldX(screenX: Float): Double = screenX / zoom + originX
    fun toWorldY(screenY: Float): Double = screenY / zoom + originY
    fun toScreenX(worldX: Double): Float = ((worldX - originX) * zoom).toFloat()
    fun toScreenY(worldY: Double): Float = ((worldY - originY) * zoom).toFloat()

    /** `[minX, minY, maxX, maxY]` into [out]. */
    fun visibleWorld(out: FloatArray): FloatArray {
        out[0] = originX.toFloat()
        out[1] = originY.toFloat()
        out[2] = (originX + viewportWidth / zoom).toFloat()
        out[3] = (originY + viewportHeight / zoom).toFloat()
        return out
    }

    /** [STEP] per notch around the viewport point. Eased unless [smooth] is false. */
    fun zoomAt(notches: Double, screenX: Float, screenY: Float, smooth: Boolean = true) {
        if (!notches.isFinite() || notches == 0.0) return
        flying = false
        anchorScreenX = screenX.toDouble()
        anchorScreenY = screenY.toDouble()
        anchorWorldX = toWorldX(screenX)
        anchorWorldY = toWorldY(screenY)
        targetZoom = clampZoom(targetZoom * STEP.pow(notches))
        if (!smooth) {
            zoom = targetZoom
            applyAnchor()
        }
    }

    fun panBy(dx: Float, dy: Float) {
        if (!dx.isFinite() || !dy.isFinite()) return
        flying = false
        originX -= dx / zoom
        originY -= dy / zoom
        anchorWorldX -= dx / zoom
        anchorWorldY -= dy / zoom
    }

    /** Viewport units per second. */
    fun fling(vx: Float, vy: Float) {
        val speed = hypot(vx.toDouble(), vy.toDouble())
        if (speed < FLING_MIN_SPEED || !speed.isFinite()) return
        val scale = if (speed > FLING_MAX_SPEED) FLING_MAX_SPEED / speed else 1.0
        velocityX = vx * scale
        velocityY = vy * scale
    }

    /** A new press takes over from whatever was moving. */
    fun stop() {
        velocityX = 0.0
        velocityY = 0.0
        flying = false
        targetZoom = zoom
    }

    fun set(x: Double, y: Double, zoom: Double) {
        stop()
        this.zoom = clampZoom(if (zoom.isFinite() && zoom > 0.0) zoom else 1.0)
        targetZoom = this.zoom
        originX = if (x.isFinite()) x else 0.0
        originY = if (y.isFinite()) y else 0.0
    }

    fun centerOn(x: Double, y: Double) {
        stop()
        originX = x - viewportWidth / 2.0 / zoom
        originY = y - viewportHeight / 2.0 / zoom
    }

    /** Never zooms past 1:1, so framing one node doesn't blow it up. */
    fun frame(minX: Float, minY: Float, maxX: Float, maxY: Float, padding: Float, smooth: Boolean = true) {
        if (viewportWidth <= 0f || viewportHeight <= 0f || maxX < minX || maxY < minY) return
        val w = (maxX - minX).coerceAtLeast(1f)
        val h = (maxY - minY).coerceAtLeast(1f)
        val fit = minOf((viewportWidth - padding * 2f) / w, (viewportHeight - padding * 2f) / h).toDouble()
        stop()
        targetZoom = clampZoom(minOf(fit, 1.0))
        flyCenterX = (minX + maxX) / 2.0
        flyCenterY = (minY + maxY) / 2.0
        flying = true
        if (!smooth) {
            zoom = targetZoom
            finishFlight()
        }
    }

    /** [dt] in seconds, capped so a hitch can't fling the view. Returns whether the view changed. */
    fun update(dt: Double): Boolean {
        val step = dt.coerceIn(0.0, MAX_DT)
        if (step == 0.0) return false
        var changed = false
        if (flying) {
            val k = expSmoothing(step, EASE_RATE)
            val centerX = originX + viewportWidth / 2.0 / zoom
            val centerY = originY + viewportHeight / 2.0 / zoom
            zoom = exp(ln(zoom) + (ln(targetZoom) - ln(zoom)) * k)
            val nextX = centerX + (flyCenterX - centerX) * k
            val nextY = centerY + (flyCenterY - centerY) * k
            originX = nextX - viewportWidth / 2.0 / zoom
            originY = nextY - viewportHeight / 2.0 / zoom
            val settled = !isZooming && abs(nextX - flyCenterX) * zoom < 0.5 && abs(nextY - flyCenterY) * zoom < 0.5
            if (settled) finishFlight()
            changed = true
        } else if (isZooming) {
            val k = expSmoothing(step, EASE_RATE)
            zoom = exp(ln(zoom) + (ln(targetZoom) - ln(zoom)) * k)
            if (!isZooming) zoom = targetZoom
            applyAnchor()
            changed = true
        }
        if (velocityX != 0.0 || velocityY != 0.0) {
            originX -= velocityX * step / zoom
            originY -= velocityY * step / zoom
            anchorWorldX -= velocityX * step / zoom
            anchorWorldY -= velocityY * step / zoom
            val decay = exp(-step * FLING_DECAY)
            velocityX *= decay
            velocityY *= decay
            if (hypot(velocityX, velocityY) < FLING_STOP_SPEED) {
                velocityX = 0.0
                velocityY = 0.0
            }
            changed = true
        }
        return changed
    }

    private fun finishFlight() {
        flying = false
        zoom = targetZoom
        originX = flyCenterX - viewportWidth / 2.0 / zoom
        originY = flyCenterY - viewportHeight / 2.0 / zoom
    }

    private fun applyAnchor() {
        originX = anchorWorldX - anchorScreenX / zoom
        originY = anchorWorldY - anchorScreenY / zoom
    }

    private fun clampZoom(z: Double): Double =
        if (!z.isFinite() || z <= 0.0) 1.0.coerceIn(minZoom, maxZoom) else z.coerceIn(minZoom, maxZoom)

    companion object {
        const val DEFAULT_MIN_ZOOM = 0.1
        const val DEFAULT_MAX_ZOOM = 2.0

        const val STEP = 1.1
        private const val EASE_RATE = 18.0
        private const val ZOOM_EPSILON = 0.001
        private const val FLING_DECAY = 6.0
        private const val FLING_MIN_SPEED = 120.0
        private const val FLING_MAX_SPEED = 4000.0
        private const val FLING_STOP_SPEED = 5.0
        private const val MAX_DT = 0.1
    }
}
