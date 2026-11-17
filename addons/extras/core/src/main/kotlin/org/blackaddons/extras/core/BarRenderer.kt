package org.blackaddons.extras.core

import org.blackaddons.api.Platform
import org.blackaddons.api.ui.layout.Cell
import org.blackaddons.api.ui.layout.LayoutNode
import org.blackaddons.api.ui.render.Render
import java.awt.Color

/**
 * Draws SkyblockAddons' `bars_v2.png` bars at the local origin: tinted track, clipped fills, style overlay. Ported from
 * SkyblockAddons (MIT, © Biscuit Development).
 */
object BarRenderer {

    /** from..to as width fractions. */
    data class Fill(val from: Float, val to: Float, val color: Color)

    private val texture by lazy { Platform.renderer.createImage(TEXTURE) }

    /**
     * Height follows the 71x5 atlas aspect, so it measures without drawing. [fills] draw in order (e.g. normal + gold
     * absorption).
     */
    fun node(w: Float, emptyTint: Color, fills: List<Fill>): LayoutNode =
        Cell(w, height(w)) { x, y -> draw(x, y, w, emptyTint, fills) }

    fun height(w: Float): Float = w * SRC_H / SRC_W

    private fun draw(x: Float, y: Float, w: Float, emptyTint: Color, fills: List<Fill>) {
        val h = height(w)
        /*
         * Pill-shaped like the sprite: each layer is full-width and rounded, partial fills get scissor-clipped, so the
         * progress edge stays straight.
         */
        val radius = h / 2f
        blit(x, y, w, h, EMPTY_Y, emptyTint, radius)
        for (f in fills) {
            if (f.to <= f.from) continue
            Render.push()
            Render.pushScissor(x + w * f.from, y, w * (f.to - f.from), h)
            blit(x, y, w, h, FILL_Y, f.color, radius)
            Render.popScissor()
            Render.pop()
        }
        blit(x, y, w, h, OVERLAY_Y, null, radius)
    }

    /** Inset half a texel, otherwise the renderer's overscan bleeds neighboring atlas rows into a seam. */
    private fun blit(x: Float, y: Float, w: Float, h: Float, srcY: Float, tint: Color?, radius: Float) {
        Platform.renderer.image(
            texture, x, y, w, h,
            SRC_X + INSET, srcY + INSET, SRC_W - 2 * INSET, SRC_H - 2 * INSET,
            TEX_W, TEX_H, tint, radius,
        )
    }

    // 80x50 atlas, 71x5 sprites at these v-offsets.
    private const val TEXTURE = "assets/blackaddons-extras/bars_v2.png"
    private const val TEX_W = 80f
    private const val TEX_H = 50f
    private const val SRC_X = 1f
    private const val SRC_W = 71f
    private const val SRC_H = 5f
    private const val EMPTY_Y = 1f
    private const val FILL_Y = 7f
    private const val OVERLAY_Y = 13f

    private const val INSET = 0.5f
}
