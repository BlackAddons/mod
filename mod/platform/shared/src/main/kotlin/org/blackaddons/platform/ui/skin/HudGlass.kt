package org.blackaddons.platform.ui.skin

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import org.blackaddons.api.ui.theme.Glass
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.util.color.fade

/**
 * HUD text boxes and the death screen in glass. Each replaces one vanilla fill, so the text, layout and other mods'
 * changes to them stay. The HUD mixins call in. Render thread only.
 */
object HudGlass {

    @JvmField var tabList = false
    @JvmField var scoreboard = false
    @JvmField var actionBar = false
    @JvmField var deathScreen = false

    private var headerTop = 0

    /**
     * The tab list's header, body and footer fills, each a part of one padded panel: the parts join square where a
     * header or footer follows, so the per-player cells drawn after the body still land on top. False keeps vanilla's.
     */
    fun tabHeader(g: GuiGraphicsExtractor, x0: Int, y0: Int, x1: Int, y1: Int): Boolean =
        tabPart(g, x0, y0 - SkinGlass.PAD, x1, y1, SkinGlass.BOTTOM)

    fun tabBody(g: GuiGraphicsExtractor, x0: Int, y0: Int, x1: Int, y1: Int, header: Boolean, footer: Boolean): Boolean {
        val top = if (header) y0 else y0 - SkinGlass.PAD
        val bottom = if (footer) y1 else y1 + SkinGlass.PAD
        return tabPart(g, x0, top, x1, bottom, (if (header) SkinGlass.TOP else 0) or (if (footer) SkinGlass.BOTTOM else 0))
    }

    fun tabFooter(g: GuiGraphicsExtractor, x0: Int, y0: Int, x1: Int, y1: Int): Boolean =
        tabPart(g, x0, y0, x1, y1 + SkinGlass.PAD, SkinGlass.TOP)

    private fun tabPart(g: GuiGraphicsExtractor, x0: Int, y0: Int, x1: Int, y1: Int, square: Int): Boolean {
        if (!tabList) return false
        val x = x0 - SkinGlass.PAD
        SkinGlass.panel(g, x, y0, x1 + SkinGlass.PAD - x, y1 - y0, Theme.RADIUS_SMALL, square = square)
        return true
    }

    /** The sidebar's title band: skipped, the body's panel reaches up to cover it. */
    fun scoreHeader(y0: Int): Boolean {
        if (!scoreboard) return false
        headerTop = y0
        return true
    }

    /** The sidebar's body: one padded panel from the title down, docked square to the screen's edge it touches. */
    fun scoreBody(g: GuiGraphicsExtractor, x0: Int, x1: Int, y1: Int, color: Int): Boolean {
        if (!scoreboard) return false
        if (color ushr 24 == 0) return true
        val x = x0 - SkinGlass.PAD
        val right = x1 + SkinGlass.PAD
        val docked = right >= g.guiWidth()
        val w = (if (docked) g.guiWidth() else right) - x
        val top = headerTop - SkinGlass.PAD
        val square = if (docked) SkinGlass.RIGHT else 0
        SkinGlass.panel(g, x, top, w, y1 + SkinGlass.PAD - top, Theme.RADIUS_SMALL, square = square)
        return true
    }

    /** The action bar's text, on a pill that fades with it. False keeps vanilla's backdrop. */
    fun actionBar(g: GuiGraphicsExtractor, font: Font, text: Component, x: Int, y: Int, width: Int, color: Int): Boolean {
        if (!actionBar) return false
        val h = font.lineHeight + PILL_PAD_Y * 2
        SkinGlass.panel(g, x - PILL_PAD_X, y - PILL_PAD_Y, width + PILL_PAD_X * 2, h, h / 2f, (color ushr 24) / 255f)
        g.text(font, text, x, y, color, true)
        return true
    }

    /** The death screen's red gradient in the theme, over the blurred world. False keeps vanilla's. */
    fun death(g: GuiGraphicsExtractor, width: Int, height: Int): Boolean {
        if (!deathScreen) return false
        SkinGlass.blur(g)
        g.fillGradient(0, 0, width, height, fade(Theme.DANGER.rgb, DEATH_TOP), SkinGlass.tone(Glass.BACKDROP.fill()))
        return true
    }

    private const val PILL_PAD_X = 6
    private const val PILL_PAD_Y = 3
    private const val DEATH_TOP = 0.35f
}
