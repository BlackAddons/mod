package org.blackaddons.api.util.color
import java.awt.Color

/** All chroma hue math. Hue follows wall-clock time, so same-[speed] colors stay in phase; [offsetCycles] shifts it. */
object Chroma {

    /** Keeps near-white/black bases visible. */
    const val SV_FLOOR = 0.5f

    /** Hue cycles per pixel of local layout, so HUD scale doesn't flatten it. Smaller = wider bands. */
    const val DEFAULT_SPATIAL = 0.010f

    /** 0 = horizontal, 90 = vertical, -45 = diagonal. */
    const val DEFAULT_ANGLE = -45f

    /** 0..1; a cycle takes `3/speed` seconds, matching the frontend. */
    fun hue(speed: Float, offsetCycles: Float = 0f): Float {
        val seconds = System.currentTimeMillis() / 1000.0
        var h = ((seconds * speed / 3.0) + offsetCycles).rem(1.0).toFloat()
        if (h < 0f) h += 1f
        return h
    }

    /** [base] with the hue replaced. */
    fun color(base: Color, speed: Float, offsetCycles: Float = 0f, allowAlpha: Boolean = true): Color {
        val hsb = Color.RGBtoHSB(base.red, base.green, base.blue, null)
        val sat = hsb[1].coerceAtLeast(SV_FLOOR)
        val bri = hsb[2].coerceAtLeast(SV_FLOOR)
        val rgb = Color.HSBtoRGB(hue(speed, offsetCycles), sat, bri) and 0x00FFFFFF
        val alpha = if (allowAlpha) base.alpha else 255
        return Color((alpha shl 24) or rgb, true)
    }
}
