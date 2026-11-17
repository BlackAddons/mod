package org.blackaddons.core.control

import java.util.Random
import kotlin.math.sqrt

/* Ornstein-Uhlenbeck wander around zero. [step] returns the change. */
internal class Wander(private val random: Random) {
    var value = 0.0
        private set

    fun step(dt: Double, time: Double, typical: Double): Double {
        val before = value
        value += -value * dt / time + typical * sqrt(2 / time) * sqrt(dt) * random.nextGaussian()
        return value - before
    }
}
