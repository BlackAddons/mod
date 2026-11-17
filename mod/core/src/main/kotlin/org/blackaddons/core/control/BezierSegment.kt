package org.blackaddons.core.control

import org.blackaddons.api.player.RotationParams
import org.blackaddons.api.util.nearZero
import org.blackaddons.api.world.ViewAngles
import java.util.Random
import kotlin.math.sqrt

/**
 * Humanized cubic Bezier to a goal. Control points are fixed at creation. The goal comes with each [sample], so a
 * moving target doesn't need a rebuild. Deterministic, the driver adds micro-noise.
 */
internal class BezierSegment(
    private val startYaw: Float,
    private val startPitch: Float,
    goalYaw: Float,
    goalPitch: Float,
    params: RotationParams,
    random: Random,
    /** The angles of the next node, so cp2 leans into it. */
    nextDir: Pair<Float, Float>? = null,
) {
    private val cp1Yaw: Float
    private val cp1Pitch: Float
    private val cp2Yaw: Float
    private val cp2Pitch: Float
    private val smoothness = params.smoothness.coerceIn(0f, 1f)
    private val easing = params.easing

    /** Degrees. The driver calculates a duration from it. */
    val distance: Float

    init {
        val dy = goalYaw - startYaw
        val dp = goalPitch - startPitch
        distance = sqrt(dy * dy + dp * dp)

        var perpYaw = -dp
        var perpPitch = dy
        val len = sqrt(perpYaw * perpYaw + perpPitch * perpPitch)
        if (!len.nearZero(1e-3f)) {
            perpYaw /= len
            perpPitch /= len
        } else {
            perpYaw = 1f
            perpPitch = 0f
        }

        if (params.humanize) {
            val curveScale = distance * params.curve * 0.5f
            val bow1 = (random.nextFloat() * 2f - 1f) * curveScale
            val bow2 = (random.nextFloat() * 2f - 1f) * curveScale
            val phaseJitter = random.nextFloat() * 0.2f - 0.1f

            cp1Yaw = startYaw + dy * (0.33f + phaseJitter) + perpYaw * bow1
            cp1Pitch = startPitch + dp * (0.33f + phaseJitter) + perpPitch * bow1

            if (nextDir != null) {
                val nextDyaw = ViewAngles.yawDelta(goalYaw, nextDir.first)
                val nextDpitch = nextDir.second - goalPitch
                cp2Yaw = goalYaw - nextDyaw * 0.2f + perpYaw * bow2
                cp2Pitch = goalPitch - nextDpitch * 0.2f + perpPitch * bow2
            } else {
                val overshoot = 0.8f + random.nextFloat() * 0.35f
                cp2Yaw = startYaw + dy * overshoot + perpYaw * bow2
                cp2Pitch = startPitch + dp * overshoot + perpPitch * bow2
            }
        } else {
            cp1Yaw = startYaw + dy * 0.333f
            cp1Pitch = startPitch + dp * 0.333f
            cp2Yaw = startYaw + dy * 0.666f
            cp2Pitch = startPitch + dp * 0.666f
        }
    }

    fun sample(t: Float, goalYaw: Float, goalPitch: Float): Pair<Float, Float> {
        val e = ease(t.coerceIn(0f, 1f))
        val u = 1f - e
        val tt = e * e
        val uu = u * u
        val uuu = uu * u
        val ttt = tt * e

        val yaw = uuu * startYaw + 3f * uu * e * cp1Yaw + 3f * u * tt * cp2Yaw + ttt * goalYaw
        val pitch = uuu * startPitch + 3f * uu * e * cp1Pitch + 3f * u * tt * cp2Pitch + ttt * goalPitch
        return yaw to pitch
    }

    /** Blends a constant rate toward the easing by [smoothness]. */
    private fun ease(t: Float): Float {
        if (smoothness <= 0f) return t
        return t + (easing.apply(t) - t) * smoothness
    }
}
