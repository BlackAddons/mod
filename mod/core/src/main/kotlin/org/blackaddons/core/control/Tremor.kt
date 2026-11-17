package org.blackaddons.core.control

import java.util.Random
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.sqrt

/* Hand tremor as a damped resonator in mouse counts (7-10 Hz narrowband). Substepped to stay stable at low FPS. */
internal class Tremor(private val random: Random) {
    private var position = 0.0
    private var velocity = 0.0

    fun step(dt: Double, hz: Double, rms: Double): Double {
        val omega = 2 * PI * hz
        val drive = rms * sqrt(4 * DAMPING * omega * omega * omega)
        val substeps = ceil(omega * dt / 0.5).toInt().coerceAtLeast(1)
        val h = dt / substeps
        val before = position
        repeat(substeps) {
            velocity += (-omega * omega * position - 2 * DAMPING * omega * velocity) * h + drive * sqrt(h) * random.nextGaussian()
            position += velocity * h
        }
        return position - before
    }

    private companion object {
        const val DAMPING = 0.15
    }
}
