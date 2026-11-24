package org.blackaddons.platform.ui.skin

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import org.blackaddons.api.ui.theme.Glass
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.util.color.fade
import org.blackaddons.platform.ui.render.BakedGlass

/**
 * Menu textures in glass, looked up by texture id at vanilla's textured `blit` funnel: menu and list backgrounds turn
 * into see-through fills, header and footer separators into a hairline, and the game mode switcher and advancements
 * window into cards under their texture's marks. Unknown textures cost one map lookup. Also the fills of list
 * selections, social interactions rows and report boxes. Render thread only.
 */
object MenuGlass {

    @JvmField var enabled = false

    private enum class Kind { BACKGROUND, LIST, HEADER, FOOTER, PANEL, WINDOW }

    private val kinds = HashMap<Identifier, Kind>().apply {
        for (prefix in listOf("", "inworld_")) {
            put(gui("${prefix}menu_background"), Kind.BACKGROUND)
            put(gui("${prefix}menu_list_background"), Kind.LIST)
            put(gui("${prefix}header_separator"), Kind.HEADER)
            put(gui("${prefix}footer_separator"), Kind.FOOTER)
        }
        put(gui("container/gamemode_switcher"), Kind.PANEL)
        put(gui("advancements/window"), Kind.WINDOW)
    }

    /** True when the blit was drawn as glass and vanilla's should be skipped. */
    fun blit(
        g: GuiGraphicsExtractor, texture: Identifier, x: Int, y: Int, u: Float, v: Float, w: Int, h: Int,
        texW: Int, texH: Int, color: Int,
    ): Boolean {
        if (!enabled) return false
        val kind = kinds[texture] ?: return false
        val alpha = (color ushr 24) / 255f
        when (kind) {
            Kind.BACKGROUND -> g.fill(x, y, x + w, y + h, fade(SkinGlass.tone(Glass.BACKDROP.fill()), alpha))
            Kind.LIST -> g.fill(x, y, x + w, y + h, fade(SkinGlass.tone(listFill()), alpha))
            Kind.HEADER -> g.fill(x, y + h - 1, x + w, y + h, fade(Theme.BORDER.rgb, alpha))
            Kind.FOOTER -> g.fill(x, y, x + w, y + 1, fade(Theme.BORDER.rgb, alpha))
            Kind.PANEL, Kind.WINDOW -> {
                val marks = Decor.region(texture, u.toInt(), v.toInt(), w, h, texW, texH, NO_HOLES) ?: return false
                if (kind == Kind.PANEL) SkinGlass.draw(g, x, y, w, h, Glass.CARD, alpha = alpha)
                if (marks !== Decor.EMPTY) g.blit(RenderPipelines.GUI_TEXTURED, marks, x, y, 0f, 0f, w, h, w, h, w, h, color)
            }
        }
        return true
    }

    /** Before the advancements screen draws its tab's contents, which the window frame then surrounds. */
    fun advancements(g: GuiGraphicsExtractor, left: Int, top: Int) {
        if (enabled) SkinGlass.draw(g, left, top, WINDOW_WIDTH, WINDOW_HEIGHT, Glass.CARD)
    }

    /** A list's selected row, one card edged in the accent while the list has focus. */
    fun selection(g: GuiGraphicsExtractor, x0: Int, y0: Int, x1: Int, y1: Int, color: Int): Boolean {
        if (!enabled) return false
        val edge = if (color == FOCUSED_OUTLINE) Theme.ACCENT.rgb else Theme.BORDER.rgb
        BakedGlass.fill(g, x0, y0, x1 - x0, y1 - y0, Theme.RADIUS_SMALL, SkinGlass.tone(Theme.SURFACE_ACTIVE.rgb))
        BakedGlass.border(g, x0, y0, x1 - x0, y1 - y0, Theme.RADIUS_SMALL, 1f, edge)
        return true
    }

    /** The social interactions screen's player rows; a hidden or blocked player's row is fainter. */
    fun playerRow(g: GuiGraphicsExtractor, x0: Int, y0: Int, x1: Int, y1: Int, color: Int): Boolean {
        if (!enabled || (color != PLAYER_ROW && color != PLAYER_ROW_REMOVED)) return false
        SkinGlass.draw(g, x0, y0, x1 - x0, y1 - y0, Glass.CELL, alpha = if (color == PLAYER_ROW) 1f else REMOVED_ALPHA)
        return true
    }

    /** A boxed text area, like the report reason's description. */
    fun box(g: GuiGraphicsExtractor, x0: Int, y0: Int, x1: Int, y1: Int): Boolean {
        if (!enabled) return false
        SkinGlass.draw(g, x0, y0, x1 - x0, y1 - y0, Glass.TOOLTIP)
        return true
    }

    /** Vanilla's dark gray titles vanish on the card. */
    fun label(color: Int): Int = if (enabled && color == VANILLA_LABEL) Theme.TEXT_SECONDARY.rgb else color

    private fun listFill(): Int = (LIST_ALPHA shl 24) or (Theme.BACKGROUND.rgb and 0xFFFFFF)

    private fun gui(path: String): Identifier = Identifier.withDefaultNamespace("textures/gui/$path.png")

    private const val VANILLA_LABEL = -12566464
    private const val FOCUSED_OUTLINE = -1
    private const val PLAYER_ROW = 0xFF4A4A4A.toInt()
    private const val PLAYER_ROW_REMOVED = 0xFF303030.toInt()
    private const val REMOVED_ALPHA = 0.55f
    private const val LIST_ALPHA = 96
    private const val WINDOW_WIDTH = 252
    private const val WINDOW_HEIGHT = 140
    private val NO_HOLES = IntArray(0)
}
