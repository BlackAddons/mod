package org.blackaddons.core.font

import java.awt.Color
import java.nio.file.Path

internal data class GlobalFontDraft(
    val rasterSize: Double,
    val oversample2d: Double,
    val oversampleWorld: Double,
    val shiftX: Double,
    val shiftY: Double,
    val letterSpacing: Double,
    val forceBold: Boolean,
    val forceItalic: Boolean,
    val shadowEnabled: Boolean,
    val shadowColor: Color,
    val shadowX: Double,
    val shadowY: Double,
    val outlineEnabled: Boolean,
    val outlineColor: Color,
    val outlineWidth: Double,
) {
    fun coerced(): GlobalFontDraft = copy(
        rasterSize = rasterSize.coerceIn(RASTER_SIZE),
        oversample2d = if (oversample2d < OVERSAMPLE.start) AUTO_OVERSAMPLE else oversample2d.coerceIn(OVERSAMPLE),
        oversampleWorld = oversampleWorld.coerceIn(OVERSAMPLE),
        shiftX = shiftX.coerceIn(SHIFT_X),
        shiftY = shiftY.coerceIn(SHIFT_Y),
        letterSpacing = letterSpacing.coerceIn(LETTER_SPACING),
        shadowX = shadowX.coerceIn(SHADOW_OFFSET),
        shadowY = shadowY.coerceIn(SHADOW_OFFSET),
        outlineWidth = outlineWidth.coerceIn(OUTLINE_WIDTH),
    )

    companion object {
        val RASTER_SIZE = 6.0..32.0
        val OVERSAMPLE = 1.0..32.0

        /* 2D text follows the GUI scale, so glyph texels land 1:1 on screen pixels. */
        const val AUTO_OVERSAMPLE = 0.0
        val OVERSAMPLE_2D = AUTO_OVERSAMPLE..OVERSAMPLE.endInclusive

        val SHIFT_X = -2.0..2.0
        val SHIFT_Y = -4.0..4.0
        val LETTER_SPACING = -4.0..12.0
        val SHADOW_OFFSET = -4.0..4.0
        val OUTLINE_WIDTH = 0.1..1.0

        const val MAX_RASTERIZED_EM = 384.0

        val DEFAULT = GlobalFontDraft(
            rasterSize = 11.0,
            oversample2d = AUTO_OVERSAMPLE,
            oversampleWorld = 32.0,
            shiftX = 0.0,
            shiftY = 0.0,
            letterSpacing = 0.0,
            forceBold = false,
            forceItalic = false,
            shadowEnabled = true,
            shadowColor = Color(0, 0, 0, 160),
            shadowX = 1.0,
            shadowY = 1.0,
            outlineEnabled = false,
            outlineColor = Color(0, 0, 0, 220),
            outlineWidth = 0.25,
        )

        val WORLD_DEFAULT = DEFAULT.copy(oversample2d = 1.0, shadowEnabled = false)
    }
}

internal fun GlobalFontDraft.toRequest(
    files: List<Path>,
    bundledResource: String?,
): GlobalFontRequest {
    val maxOversample = minOf(GlobalFontDraft.OVERSAMPLE.endInclusive, GlobalFontDraft.MAX_RASTERIZED_EM / rasterSize).toFloat()
    return GlobalFontRequest(
        files = files,
        bundledResource = bundledResource,
        rasterSize = rasterSize.toFloat(),
        oversample2d = minOf(oversample2d.toFloat(), maxOversample),
        oversampleWorld = minOf(oversampleWorld.toFloat(), maxOversample),
        maxOversample = maxOversample,
        shiftX = shiftX.toFloat(),
        shiftY = shiftY.toFloat(),
        letterSpacing = letterSpacing.toFloat(),
        forceBold = forceBold,
        forceItalic = forceItalic,
        shadow = FontEffect(shadowEnabled, shadowColor.rgb, shadowX.toFloat(), shadowY.toFloat()),
        outline = FontEffect(
            outlineEnabled,
            outlineColor.rgb,
            width = outlineWidth.coerceAtMost(GlobalFontDraft.OUTLINE_WIDTH.endInclusive).toFloat(),
        ),
    )
}
