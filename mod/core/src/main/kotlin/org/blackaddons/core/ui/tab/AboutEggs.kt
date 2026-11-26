package org.blackaddons.core.ui.tab

import org.blackaddons.api.util.color.ColorStop
import org.blackaddons.core.ui.tab.eggs.*
import java.awt.Color

/**
 * Id -> egg switchboard. One file per egg under `eggs/`. Every egg is a pure function of progress 0..1 (the controller
 * owns the tween), so they reverse for free and hold no state.
 */
internal object AboutEggs {

    /** Names without one aren't clickable and don't light up. */
    fun exists(id: String): Boolean = id in WITH_EGGS

    fun revealMs(id: String): Long = when (id) {
        "dreamcast" -> DreamcastEgg.REVEAL_MS
        "atklxve" -> AtkLxveEgg.REVEAL_MS
        else -> REVEAL_MS
    }

    /** Room needed below the name. Scales with [progress], so the list opens instead of jumping. */
    fun extraHeight(id: String, progress: Float): Float = when (id) {
        "autismo" -> AutismoEgg.extraHeight(progress)
        else -> 0f
    }

    /** Drawn over the page, not in the row. */
    fun drawFlight(id: String, originX: Float, originY: Float, x: Float, y: Float, w: Float, h: Float, progress: Float) {
        if (id != "dreamcast") return
        DreamcastEgg.drawFlight(originX, originY, x, y, w, h, progress)
    }

    fun drawRat(x: Float, y: Float, w: Float, h: Float, progress: Float) {
        if (progress <= 0f) return
        TitleEgg.draw(x, y, w, h, progress)
    }

    fun drawBelow(id: String, x: Float, y: Float, progress: Float) {
        if (progress <= 0f) return
        when (id) {
            "autismo" -> AutismoEgg.drawBelow(x, y, progress)
        }
    }

    fun draw(id: String, name: String, x: Float, y: Float, progress: Float, color: Color, brand: List<ColorStop>): Float =
        when {
            progress <= 0f -> plain(name, x, y, color)
            id == "blackum" -> BlackumEgg.draw(name, x, y, progress, color, brand)
            id == "noamm" -> NoammEgg.draw(name, x, y, progress, color)
            id == "autismo" -> AutismoEgg.draw(name, x, y, progress, color)
            id == "dreamcast" -> DreamcastEgg.draw(name, x, y, progress, color)
            id == "atklxve" -> AtkLxveEgg.draw(name, x, y, progress, color)
            else -> plain(name, x, y, color)
        }

    private val WITH_EGGS = setOf("blackum", "noamm", "autismo", "title", "dreamcast", "atklxve")

    /** Long enough to look like a reveal and not like a glitch. */
    private const val REVEAL_MS = 420L
}
