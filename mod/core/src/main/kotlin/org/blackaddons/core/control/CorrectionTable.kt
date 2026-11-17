package org.blackaddons.core.control

import org.blackaddons.core.control.Submovement.Companion.ENDPOINT_ASPECT
import java.util.Random
import kotlin.math.hypot
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.pow

/*
 * Expected corrections at a distance/width [ratio], from Monte Carlo per noise and undershoot, so the flick timing
 * keeps the whole acquisition on Fitts's law.
 */
internal object CorrectionTable {
    const val MAX_CORRECTIONS = 3
    const val CORRECTION_GAIN = 0.93

    private const val BUCKETS = 32
    private const val RUNS = 1000
    private const val LOG_MIN = -2.0
    private const val LOG_MAX = 7.0
    private var noise = Double.NaN
    private var gain = Double.NaN
    private val table = DoubleArray(BUCKETS)

    fun expected(ratio: Double, endpointNoise: Double, primaryGain: Double): Double {
        if (endpointNoise != noise || primaryGain != gain) build(endpointNoise, primaryGain)
        val x = ((log2(ratio.coerceAtLeast(1e-9)) - LOG_MIN) / (LOG_MAX - LOG_MIN) * (BUCKETS - 1)).coerceIn(0.0, BUCKETS - 1.0)
        val i = min(x.toInt(), BUCKETS - 2)
        return table[i] + (table[i + 1] - table[i]) * (x - i)
    }

    private fun build(endpointNoise: Double, primaryGain: Double) {
        val random = Random(endpointNoise.toRawBits())
        for (b in 0 until BUCKETS) {
            val ratio = 2.0.pow(LOG_MIN + (LOG_MAX - LOG_MIN) * b / (BUCKETS - 1))
            var total = 0
            repeat(RUNS) {
                var along = ratio - (primaryGain * ratio + endpointNoise * ratio * random.nextGaussian())
                var cross = endpointNoise * ratio / ENDPOINT_ASPECT * random.nextGaussian()
                var n = 0
                while (hypot(along, cross) > 0.5 && n < MAX_CORRECTIONS) {
                    val left = hypot(along, cross)
                    along = left - (CORRECTION_GAIN * left + endpointNoise * left * random.nextGaussian())
                    cross = endpointNoise * left / ENDPOINT_ASPECT * random.nextGaussian()
                    n++
                }
                total += n
            }
            table[b] = total.toDouble() / RUNS
        }
        noise = endpointNoise
        gain = primaryGain
    }
}
