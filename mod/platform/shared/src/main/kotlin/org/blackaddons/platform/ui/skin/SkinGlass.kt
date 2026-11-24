package org.blackaddons.platform.ui.skin

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import org.blackaddons.api.ui.theme.Glass
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.util.color.fade
import org.blackaddons.platform.mixin.gui.GuiGraphicsExtractorAccessor
import org.blackaddons.platform.mixin.gui.GuiRenderStateAccessor
import org.blackaddons.platform.ui.render.BakedGlass
import kotlin.math.ceil

/**
 * The glass look of the vanilla skins (the mod's own screens draw [BakedGlass] directly): every fill is see-through by
 * [opacity], in the theme's colors with [tint] or a neutral smoke without, and borderless panels get a faint edge.
 * With [blur], in-game screens blur the world behind them like vanilla's menus do. Render thread only.
 */
object SkinGlass {

    @JvmField var backdrops = false
    @JvmField var tint = true
    @JvmField var opacity = 0.7f
    @JvmField var blur = true

    fun draw(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, style: Glass, hover: Float = 0f, alpha: Float = 1f) {
        BakedGlass.draw(g, x, y, w, h, style, hover, alpha, tone(style.fill()))
        if (!style.hasBorder && style != Glass.CELL) {
            BakedGlass.border(g, x, y, w, h, style.radius, 1f, fade(Theme.BORDER.rgb, EDGE_ALPHA * alpha))
        }
    }

    /**
     * A [Glass.HUD] panel at any [radius], for HUD text boxes too tight for its own corners. Its [square] sides
     * ([TOP], [BOTTOM], [RIGHT]) run straight, for a panel that joins another one or the screen's edge.
     */
    fun panel(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, radius: Float, alpha: Float = 1f, square: Int = 0) {
        if (square != 0) g.enableScissor(x, y, x + w, y + h)
        val r = ceil(radius).toInt()
        val y0 = if (square and TOP != 0) y - r else y
        val y1 = if (square and BOTTOM != 0) y + h + r else y + h
        val x1 = if (square and RIGHT != 0) x + w + r else x + w
        BakedGlass.fill(g, x, y0, x1 - x, y1 - y0, radius, fade(tone(Glass.HUD.fill()), alpha))
        BakedGlass.border(g, x, y0, x1 - x, y1 - y0, radius, 1f, fade(Theme.BORDER.rgb, EDGE_ALPHA * alpha))
        if (square != 0) g.disableScissor()
    }

    /** [argb] at [opacity], gray of the same lightness without [tint]. */
    fun tone(argb: Int): Int {
        val a = ((argb ushr 24) * opacity).toInt() shl 24
        if (tint) return a or (argb and RGB)
        val r = argb shr 16 and 0xFF
        val gr = argb shr 8 and 0xFF
        val b = argb and 0xFF
        val l = (r * 299 + gr * 587 + b * 114) / 1000
        return a or (l shl 16) or (l shl 8) or l
    }

    /** In-game screens' dim: the world blurred, then the glass backdrop. False keeps vanilla's gradient. */
    fun backdrop(g: GuiGraphicsExtractor, width: Int, height: Int): Boolean {
        if (!backdrops) return false
        blur(g)
        g.fill(0, 0, width, height, tone(Glass.BACKDROP.fill()))
        return true
    }

    /* Vanilla throws on a second blur in one frame; the menu blur option 0 means the player turned blur off. */
    fun blur(g: GuiGraphicsExtractor) {
        if (!blur || Minecraft.getInstance().options.menuBackgroundBlurriness < 1) return
        val state = (g as GuiGraphicsExtractorAccessor).`blackaddons$getGuiRenderState`()
        if ((state as GuiRenderStateAccessor).`blackaddons$getFirstStratumAfterBlur`() == Int.MAX_VALUE) {
            g.blurBeforeThisStratum()
        }
    }

    /* Space HUD panels leave around their text; vanilla's boxes stop at the glyphs. */
    const val PAD = 3
    const val TOP = 1
    const val BOTTOM = 2
    const val RIGHT = 4

    private const val RGB = 0xFFFFFF
    private const val EDGE_ALPHA = 0.6f
}
