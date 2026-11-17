package org.blackaddons.api.ui.widget

import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Theme
import java.awt.Color

/** Bordered color chip. The fill sits on the surface so translucent colors don't read lighter. */
object Swatch {

    fun draw(x: Float, y: Float, w: Float, h: Float, color: Color) {
        Render.rect(x, y, w, h, Theme.BORDER, Theme.RADIUS_SMALL)
        Render.rect(x + 1f, y + 1f, w - 2f, h - 2f, Theme.SURFACE, Theme.RADIUS_SMALL)
        Render.rect(x + 1f, y + 1f, w - 2f, h - 2f, color, Theme.RADIUS_SMALL)
    }
}
