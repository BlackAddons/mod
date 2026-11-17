package org.blackaddons.platform.impl.player.rotation
import net.minecraft.client.Minecraft
import kotlin.math.roundToLong

/**
 * Real mice turn in whole counts (`turnPlayer` scales by `sens`, `Entity.turn` multiplies by 0.15), and anticheats
 * check that GCD, so programmatic turns snap onto it.
 */
internal object MouseGrid {

    private val mc get() = Minecraft.getInstance()

    // Degrees per count before the 0.15. Float constants widen like vanilla's.
    private val sens: Double
        get() {
            val f = mc.options.sensitivity().get() * 0.6f + 0.2f
            val cubed = f * f * f
            val scoping = mc.options.cameraType.isFirstPerson && mc.player?.isScoping == true
            return if (scoping) cubed else cubed * 8.0
        }

    val step: Float get() = (sens * TURN_SCALE).toFloat()

    /** Nearest angle reachable from [from] in whole counts. */
    fun snap(from: Float, to: Float): Float {
        val sens = sens
        val counts = ((to - from) / (sens * TURN_SCALE)).roundToLong()
        return from + (counts * sens).toFloat() * TURN_SCALE
    }

    private const val TURN_SCALE = 0.15f
}
