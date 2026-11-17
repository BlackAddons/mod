package org.blackaddons.api.cosmetics

import org.blackaddons.api.util.color.ColorStop

/** One text color, mirroring the backend's `color` object: solid, gradient or chroma, one live at a time. */
data class ColorSpec(
    /** `0xRRGGBB`, or null when not solid. */
    val solidColor: Int? = null,
    val gradient: List<ColorStop> = emptyList(),
    val chroma: Boolean = false,
    val animated: Boolean = false,
    val speed: Float = 1f,
    /** Spreads one gradient over the text. False: letters share one cycling color (vanilla glyphs are single-color). */
    val wholeTextGradient: Boolean = false,
) {
    val overrides: Boolean
        get() = solidColor != null || gradient.isNotEmpty() || chroma

    companion object {
        val VANILLA = ColorSpec()
    }
}
