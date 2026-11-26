package org.blackaddons.core.control

import java.util.Random
import kotlin.math.*

/*
 * One flick or correction: two minimum-jerk halves joined at [skew], a scattered endpoint, a handed [arc], and mouse
 * lifts past [reachCounts]. [advance] only returns the part for this frame.
 */
internal class Submovement(
    yaw: Double,
    pitch: Double,
    duration: Double,
    along: Double,
    private val skew: Double,
    endpointNoise: Double,
    arc: Double,
    step: Float,
    reachCounts: Double,
    random: Random,
) {
    private val parYaw: Double
    private val parPitch: Double
    private val amplitude: Double
    private val lateral: Double
    private val bow: Double
    private val strokes: Int
    private val strokeTime: Double
    private val liftTime: Double
    private var offsetYaw = 0.0
    private var offsetPitch = 0.0

    var lifted = false
        private set

    init {
        val distance = hypot(yaw, pitch).coerceAtLeast(1e-9)
        val quantum = step / 2.0
        val scatter = endpointNoise * distance
        val sigPar = sqrt(quantum * quantum + scatter * scatter)
        parYaw = yaw / distance
        parPitch = pitch / distance
        amplitude = along * distance + sigPar * random.nextGaussian()
        lateral = sigPar / ENDPOINT_ASPECT * random.nextGaussian()
        bow = arc * abs(yaw)
        strokes = if (step > 0f) ceil(abs(amplitude) / step / reachCounts).toInt().coerceAtLeast(1) else 1
        strokeTime = duration / strokes
        liftTime = if (strokes > 1) 0.10 + random.nextDouble() * 0.15 else 0.0
    }

    val total: Double get() = strokes * strokeTime + (strokes - 1) * liftTime

    fun advance(t: Double): Pair<Double, Double> {
        val span = strokeTime + liftTime
        val index = min(floor(t / span).toInt(), strokes - 1)
        val local = t - index * span
        lifted = local > strokeTime && index < strokes - 1
        val p = (index + profile(min(1.0, local / strokeTime))) / strokes
        val along = amplitude * p
        val cross = lateral * p + bow * 4 * p * (1 - p)
        val yaw = along * parYaw - cross * parPitch
        val pitch = along * parPitch + cross * parYaw
        val delta = (yaw - offsetYaw) to (pitch - offsetPitch)
        offsetYaw = yaw
        offsetPitch = pitch
        return delta
    }

    /* The planned displacement not made yet. */
    fun remaining(): Pair<Double, Double> {
        val yaw = amplitude * parYaw - lateral * parPitch
        val pitch = amplitude * parPitch + lateral * parYaw
        return (yaw - offsetYaw) to (pitch - offsetPitch)
    }

    private fun profile(u: Double): Double =
        if (u < skew) 2 * skew * minimumJerk(u / (2 * skew))
        else skew + 2 * (1 - skew) * (minimumJerk(0.5 + (u - skew) / (2 * (1 - skew))) - 0.5)

    private fun minimumJerk(t: Double): Double = t * t * t * (10 - 15 * t + 6 * t * t)

    companion object {
        /* Scatter along the path vs across it. */
        const val ENDPOINT_ASPECT = 2.3
    }
}
