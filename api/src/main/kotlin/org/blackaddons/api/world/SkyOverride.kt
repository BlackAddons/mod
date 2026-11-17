package org.blackaddons.api.world

import java.awt.Color

/** The scenes a replaced overworld sky can show. */
enum class SkyStyle { LANDSCAPE, SYNTHWAVE, GALAXY }

/**
 * One frame of a replaced overworld sky. The colors are the roles in the scene's palette. [horizon] is also the fog
 * color, so far terrain fades into the sky.
 */
class SkyLook(
    val style: SkyStyle,
    val ground: Color,
    val main: Color,
    val plum: Color,
    val glow: Color,
    val horizon: Color,
    val seconds: Float,
)

/** Replaces the overworld sky without the renderer having to know who asked for it. */
fun interface SkyOverride {
    /** [sunAngle] is in radians from noon and [rain] goes from 0 to 1. Returning null keeps the vanilla sky. */
    fun look(sunAngle: Float, rain: Float): SkyLook?
}

/** A single slot: two skies cannot be blended in any sensible way, so the last registration wins. */
object SkyOverrides {

    @Volatile
    private var current: SkyOverride? = null

    fun register(override: SkyOverride) {
        current = override
    }

    fun release(override: SkyOverride) {
        if (current === override) current = null
    }

    fun look(sunAngle: Float, rain: Float): SkyLook? = current?.look(sunAngle, rain)
}
