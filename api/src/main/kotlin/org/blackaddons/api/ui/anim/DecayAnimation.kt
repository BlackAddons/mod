package org.blackaddons.api.ui.anim

import org.blackaddons.api.ui.theme.Theme
import kotlin.math.pow

/**
 * Exponential decay toward a target that may move every frame (slider fill, scroll), halving the gap every
 * [halfLifeSeconds]. The first [get] snaps to the target.
 */
class DecayAnimation(
    initial: Float = Float.NaN,
    val halfLifeSeconds: Float = Theme.ANIM_DECAY_HALFLIFE,
) {
    var value: Float = initial
    private var lastTime = 0L

    fun get(target: Float): Float {
        val now = System.currentTimeMillis()
        if (value.isNaN()) {
            value = target
        } else {
            val dt = if (lastTime == 0L) 0f else (now - lastTime) / 1000f
            value = target + (value - target) * 0.5f.pow(dt / halfLifeSeconds)
        }
        lastTime = now
        return value
    }

    fun set(newValue: Float) {
        value = newValue
        lastTime = System.currentTimeMillis()
    }
}
