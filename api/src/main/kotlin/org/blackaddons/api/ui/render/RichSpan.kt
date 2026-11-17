package org.blackaddons.api.ui.render

import java.awt.Color

/** A text run or an inline picture that wraps like a glyph. `srcW`/`srcH` of 0 mean the whole image. */
sealed interface RichSpan {
    /**
     * [color] overrides the paragraph's color (null keeps it). [scale] is the size relative to the paragraph's, in ems.
     */
    data class Text(val text: String, val color: Color? = null, val scale: Float = 1f) : RichSpan

    data class Picture(
        val path: String,
        val srcX: Float = 0f,
        val srcY: Float = 0f,
        val srcW: Float = 0f,
        val srcH: Float = 0f,
        /** Ems of the surrounding text. */
        val scale: Float = 1.2f,
    ) : RichSpan
}
