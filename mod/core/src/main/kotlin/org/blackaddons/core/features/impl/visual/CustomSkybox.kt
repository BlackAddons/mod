package org.blackaddons.core.features.impl.visual

import org.blackaddons.api.feature.Feature
import org.blackaddons.api.util.FrameClock
import org.blackaddons.api.util.color.lerpColor
import org.blackaddons.api.util.color.scaled
import org.blackaddons.api.world.SkyLook
import org.blackaddons.api.world.SkyOverride
import org.blackaddons.api.world.SkyOverrides
import org.blackaddons.api.world.SkyStyle
import org.blackaddons.core.theme.NebulaPalette
import org.blackaddons.core.theme.ThemedPalette
import java.awt.Color
import kotlin.math.cos
import kotlin.time.Duration.Companion.milliseconds

/*
 * A shader scene as the overworld sky. The platform draws it, this picks its colors (for Landscape, based on the time
 * of day).
 */
object CustomSkybox : Feature(
    description = "Replaces the overworld sky with an animated scene in the BlackAddons colors.",
), SkyOverride {

    private const val THEME = "Theme"
    private const val CUSTOM = "Custom"
    private const val COLORS = "Colors"
    private const val LANDSCAPE = "Landscape"
    private const val SYNTHWAVE = "Synthwave"
    private const val GALAXY = "Galaxy"
    private const val CLOCK_WRAP = 86_400.0
    private const val HORIZON_PLUM = 0.1f
    private const val SYNTHWAVE_HORIZON = 0.3f
    private const val GALAXY_HORIZON = 0.6f

    private val style by mode("Style", default = LANDSCAPE, modes = listOf(LANDSCAPE, SYNTHWAVE, GALAXY))
        .asDropdown()
        .withDescription(
            "Landscape = the menu's hills, following the time of day.\n" +
                "Synthwave = a striped sun over a neon canyon.\n" +
                "Galaxy = a spiral galaxy over a starry nebula.",
        )
    private val palette by mode("Palette", default = "Default", modes = listOf("Default", THEME, CUSTOM))
        .withDescription("Default = the BlackAddons colors.\nTheme = follows your UI theme's accent and background.\nCustom = your own colors below.")
        .group(COLORS)
    private val customBottom by color("Background Bottom", NebulaPalette.FLOW.bottom, allowAlpha = false).group(COLORS)
        .showIf { isCustom() && style.value == LANDSCAPE }
    private val customDeep by color("Color 1", NebulaPalette.FLOW.deep, allowAlpha = false).group(COLORS)
        .showIf { isCustom() && style.value != LANDSCAPE }
    private val customMain by color("Color 2", NebulaPalette.FLOW.main, allowAlpha = false).group(COLORS).showIf(::isCustom)
    private val customHot by color("Color 3", NebulaPalette.FLOW.hot, allowAlpha = false).group(COLORS).showIf(::isCustom)
    private val customPlum by color("Color 4", NebulaPalette.FLOW.plum, allowAlpha = false).group(COLORS).showIf(::isCustom)
    private val brightness by number("Brightness", 100.0, 20.0, 150.0, 5.0, unit = "%")
        .withDescription("Sky brightness.")

    private var seconds = 0.0
    private val frameClock = FrameClock(cap = 100.milliseconds)

    private val themed = ThemedPalette(NebulaPalette::flow)

    override fun onEnable() {
        super.onEnable()
        SkyOverrides.register(this)
    }

    override fun onDisable() {
        super.onDisable()
        SkyOverrides.release(this)
    }

    override fun look(sunAngle: Float, rain: Float): SkyLook {
        seconds = (seconds + frameClock.tick()) % CLOCK_WRAP
        val colors = when (palette.value) {
            THEME -> themed.current()
            CUSTOM -> NebulaPalette(
                NebulaPalette.FLOW.top, customBottom.value, customDeep.value, customMain.value, customHot.value, customPlum.value,
            )
            else -> NebulaPalette.FLOW
        }
        val strength = brightness.value.toFloat() / 100
        return when (style.value) {
            SYNTHWAVE -> designed(SkyStyle.SYNTHWAVE, colors, strength, horizon = colors.plum.scaled(SYNTHWAVE_HORIZON * strength))
            GALAXY -> designed(SkyStyle.GALAXY, colors, strength, horizon = colors.deep.scaled(GALAXY_HORIZON * strength))
            else -> landscape(colors, skyLight(cos(sunAngle), rain) * strength)
        }
    }

    private fun isCustom() = palette.value == CUSTOM

    private fun landscape(colors: NebulaPalette, light: Float): SkyLook {
        val main = colors.main.scaled(light)
        val plum = colors.plum.scaled(light)
        return SkyLook(
            style = SkyStyle.LANDSCAPE,
            ground = colors.bottom.scaled(light),
            main = main,
            plum = plum,
            glow = colors.hot.scaled(light),
            horizon = lerpColor(main, plum, HORIZON_PLUM),
            seconds = seconds.toFloat(),
        )
    }

    /*
     * Synthwave and Galaxy always look as designed: day and rain don't change them, only Brightness does. Ground is
     * their dark backdrop.
     */
    private fun designed(style: SkyStyle, colors: NebulaPalette, strength: Float, horizon: Color) = SkyLook(
        style = style,
        ground = colors.deep.scaled(strength),
        main = colors.main.scaled(strength),
        plum = colors.plum.scaled(strength),
        glow = colors.hot.scaled(strength),
        horizon = horizon,
        seconds = seconds.toFloat(),
    )

}

private const val NIGHT_LIGHT = 0.15f
private const val DUSK_SUN_HEIGHT = -0.2f
private const val DAY_SUN_HEIGHT = 0.3f
private const val RAIN_DIM = 0.5f

/* 1 when the sun is high and NIGHT_LIGHT when it's well below the horizon. Rain dims it like it dims vanilla's sky. */
internal fun skyLight(sunHeight: Float, rain: Float): Float {
    val t = ((sunHeight - DUSK_SUN_HEIGHT) / (DAY_SUN_HEIGHT - DUSK_SUN_HEIGHT)).coerceIn(0f, 1f)
    val day = t * t * (3 - 2 * t)
    return (NIGHT_LIGHT + (1 - NIGHT_LIGHT) * day) * (1 - rain.coerceIn(0f, 1f) * RAIN_DIM)
}
