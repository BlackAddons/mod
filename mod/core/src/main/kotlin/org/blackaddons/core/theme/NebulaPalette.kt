package org.blackaddons.core.theme

import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.util.color.scaled
import java.awt.Color

/**
 * The colors of the BlackAddons scenes (the menu background and the custom sky), by role, so every palette paints the
 * same scene.
 */
internal data class NebulaPalette(
    val top: Color,
    val bottom: Color,
    val deep: Color,
    val main: Color,
    val hot: Color,
    val plum: Color,
) {
    companion object {
        val NEBULA = NebulaPalette(
            top = Color(9, 6, 16),
            bottom = Color(3, 2, 6),
            deep = Color(60, 18, 120),
            main = Color(96, 34, 170),
            hot = Color(150, 28, 96),
            plum = Color(112, 22, 120),
        )

        /** The website's own colors, shared by every shader scene. */
        val FLOW = NebulaPalette(
            top = Color(8, 9, 14),
            bottom = Color(5, 5, 9),
            deep = Color(22, 26, 51),
            main = Color(117, 64, 209),
            hot = Color(184, 77, 140),
            plum = Color(158, 66, 173),
        )

        /** Dark shades of the accent and its warmer neighbors (violet to raspberry) over a darkened background. */
        fun nebula(accent: Color, background: Color): NebulaPalette {
            val (hue, saturation, value) = Color.RGBtoHSB(accent.red, accent.green, accent.blue, null)
            val rich = (saturation * 1.3f).coerceAtMost(1f)
            fun shade(hueShift: Float, brightness: Float) =
                Color(Color.HSBtoRGB((hue + hueShift + 1f) % 1f, rich, value * brightness))
            return NebulaPalette(
                top = background.scaled(0.45f),
                bottom = background.scaled(0.18f),
                deep = shade(0f, 0.47f),
                main = shade(0f, 0.67f),
                hot = shade(0.12f, 0.6f),
                plum = shade(0.06f, 0.5f),
            )
        }

        /**
         * Flow mixes its colors in at about a third, so they stay close to the accent's own brightness. The deep tone
         * leans cooler, like the website's navy.
         */
        fun flow(accent: Color, background: Color): NebulaPalette {
            val (hue, saturation, value) = Color.RGBtoHSB(accent.red, accent.green, accent.blue, null)
            fun shade(hueShift: Float, saturationScale: Float, brightness: Float) = Color(
                Color.HSBtoRGB((hue + hueShift + 1f) % 1f, (saturation * saturationScale).coerceAtMost(1f), value * brightness),
            )
            return NebulaPalette(
                top = background.scaled(0.4f),
                bottom = background.scaled(0.25f),
                deep = shade(-0.1f, 0.8f, 0.22f),
                main = shade(0f, 1.15f, 0.85f),
                hot = shade(0.12f, 1.1f, 0.78f),
                plum = shade(0.06f, 1.1f, 0.72f),
            )
        }
    }
}

/** Runs [derive] on the UI theme's accent and background, and only again when one of them changes. */
internal class ThemedPalette(private val derive: (accent: Color, background: Color) -> NebulaPalette) {
    private var key: Pair<Color, Color>? = null
    private var palette: NebulaPalette? = null

    fun current(): NebulaPalette {
        val now = Theme.ACCENT to Theme.BACKGROUND
        palette?.takeIf { now == key }?.let { return it }
        key = now
        return derive(now.first, now.second).also { palette = it }
    }
}
