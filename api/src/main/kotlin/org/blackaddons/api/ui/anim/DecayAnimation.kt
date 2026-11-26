package org.blackaddons.api.ui.anim

import org.blackaddons.api.ui.theme.Theme
import kotlin.math.pow
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

/**
 * Exponential decay toward a target that may move every frame (slider fill, scroll), halving the gap every
 * [halfLifeSeconds]. The first [get] snaps to the target.
 */
class DecayAnimation(
    initial: Float = Float.NaN,
    val halfLifeSeconds: Float = Theme.ANIM_DECAY_HALFLIFE,
) {
    var value: Float = initial
    private var lastTime: TimeSource.Monotonic.ValueTimeMark? = null

    fun get(target: Float): Float {
        val now = TimeSource.Monotonic.markNow()
        if (value.isNaN()) {
            value = target
        } else {
            val dt = lastTime?.let { (now - it).toDouble(DurationUnit.SECONDS).toFloat() } ?: 0f
            value = target + (value - target) * 0.5f.pow(dt / halfLifeSeconds)
        }
        lastTime = now
        return value
    }

    fun set(newValue: Float) {
        value = newValue
        lastTime = TimeSource.Monotonic.markNow()
    }
}
