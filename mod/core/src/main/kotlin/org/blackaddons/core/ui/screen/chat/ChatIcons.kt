package org.blackaddons.core.ui.screen.chat

import org.blackaddons.api.ui.render.Render
import java.awt.Color

/** Chat hover-button glyphs as vector paths (24-unit box, stroke 2, scaled at draw time). */
internal object ChatIcons {

    enum class Icon { REPLY, COPY, TRASH }

    fun draw(icon: Icon, x: Float, y: Float, size: Float, color: Color) =
        Render.icon(path(icon), x, y, size, color, thickness = STROKE)

    private fun path(icon: Icon): String = when (icon) {
        Icon.REPLY -> REPLY
        Icon.COPY -> COPY
        Icon.TRASH -> TRASH
    }

    private const val REPLY = "M9 17 4 12 9 7M20 18v-2a4 4 0 0 0-4-4H4"

    private const val COPY =
        "M10 8h10a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H10a2 2 0 0 1-2-2V10a2 2 0 0 1 2-2z" +
            "M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"

    private const val TRASH = "M3 6h18M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"

    // Thinner than 2, so details don't close up at 13 px.
    private const val STROKE = 1.7f
}
