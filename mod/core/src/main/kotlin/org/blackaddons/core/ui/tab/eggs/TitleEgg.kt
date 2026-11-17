package org.blackaddons.core.ui.tab.eggs

import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Theme

/**
 * The headline's egg. Rises and fades in (a photo popping onto a settings page reads as a bug), at whatever size the
 * caller gives: the joke shouldn't take over the screen.
 */
internal object TitleEgg {

    fun draw(x: Float, y: Float, w: Float, h: Float, progress: Float) {
        val lift = RAT_LIFT * (1f - progress)
        Render.globalAlpha(progress)
        try {
            Render.image(RAT, x, y + lift, w, h, Theme.RADIUS_SMALL)
        } finally {
            Render.globalAlpha(1f)
        }
    }

    /** A community joke of the Skyblock modding crowd, bundled instead of fetched - see the notes. */
    private const val RAT = "assets/blackaddons/textures/bigrat.jpg"
    private const val RAT_LIFT = 10f
}
