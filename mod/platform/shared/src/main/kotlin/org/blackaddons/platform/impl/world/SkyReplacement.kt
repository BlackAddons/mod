package org.blackaddons.platform.impl.world

import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.buffers.Std140SizeCalculator
import org.blackaddons.api.world.SkyLook
import org.blackaddons.api.world.SkyOverrides
import org.blackaddons.api.world.SkyStyle
import java.awt.Color
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.sin

/*
 * The replaced overworld sky for this frame, taken at sky extraction so the fog and every sky draw agree. Fog is set up
 * before extraction, so it reads the previous frame's look.
 */
internal object SkyReplacement {

    val UBO_SIZE = Std140SizeCalculator().putVec4().putVec4().putVec4().putVec4().putVec4().putVec4().get()

    private class Frame(val look: SkyLook, val sunAngle: Float, val stars: Float)

    @Volatile
    private var frame: Frame? = null

    @JvmStatic
    fun extract(overworld: Boolean, sunAngle: Float, rainBrightness: Float, starBrightness: Float) {
        val look = if (overworld && !IrisCompat.shaderPackInUse()) SkyOverrides.look(sunAngle, 1 - rainBrightness) else null
        frame = look?.let { Frame(it, sunAngle, starBrightness * rainBrightness) }
    }

    @JvmStatic
    val active: Boolean get() = frame != null

    val style: SkyStyle? get() = frame?.look?.style

    @JvmStatic
    fun horizon(): Color? = frame?.look?.horizon

    /* Fills the CustomSky block of custom_sky.glsl. False when there's nothing to draw. */
    fun write(buffer: ByteBuffer): Boolean {
        val frame = frame ?: return false
        val look = frame.look
        /* Vanilla's sun: straight up at noon, west (-X) at sunset. */
        Std140Builder.intoBuffer(buffer)
            .rgb(look.ground)
            .rgb(look.main)
            .rgb(look.plum)
            .rgb(look.glow)
            .putVec4(-sin(frame.sunAngle), cos(frame.sunAngle), 0f, frame.stars)
            .putVec4(look.seconds, 0f, 0f, 0f)
        return true
    }

    private fun Std140Builder.rgb(color: Color) = putVec4(color.red / 255f, color.green / 255f, color.blue / 255f, 1f)
}

/*
 * A part of a sky that only depends on direction (or on the position on a disc). It's rendered once, so the sky shader
 * only samples it.
 */
internal class SkyBake(val shader: String, val width: Int, val height: Int, val wrapsAround: Boolean)

private val HILLS = SkyBake("landscape_hills", 4096, 512, wrapsAround = true)
private val CANYON = SkyBake("synthwave_canyon", 2048, 2048, wrapsAround = false)
private val DISC = SkyBake("galaxy_disc", 2048, 2048, wrapsAround = false)
private val NEBULA = SkyBake("galaxy_nebula", 1024, 512, wrapsAround = true)

/* Bound as SkyBake0, SkyBake1 and so on, in this order. */
internal val SkyStyle.bakes: List<SkyBake>
    get() = when (this) {
        SkyStyle.LANDSCAPE -> LANDSCAPE_BAKES
        SkyStyle.SYNTHWAVE -> SYNTHWAVE_BAKES
        SkyStyle.GALAXY -> GALAXY_BAKES
    }

private val LANDSCAPE_BAKES = listOf(HILLS)
private val SYNTHWAVE_BAKES = listOf(CANYON)
private val GALAXY_BAKES = listOf(DISC, NEBULA)

internal val SKY_BAKE_SAMPLERS = listOf("SkyBake0", "SkyBake1")

internal val SkyStyle.shader: String get() = "${name.lowercase()}_sky"

internal val SkyStyle.define: String get() = "SKY_$name"

internal val SkyBake.define: String get() = shader.uppercase()
