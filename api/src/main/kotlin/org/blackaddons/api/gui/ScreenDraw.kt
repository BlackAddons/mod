package org.blackaddons.api.gui

/**
 * Vanilla GUI drawing without `GuiGraphics`. [ctx] is the opaque context the game passed; only valid during
 * that render pass. GUI pixels, ARGB colors.
 */
interface ScreenDraw {
    fun fill(ctx: Any, x: Int, y: Int, w: Int, h: Int, argb: Int)

    fun text(ctx: Any, text: String, x: Int, y: Int, argb: Int)

    fun textCentered(ctx: Any, text: String, centerX: Int, y: Int, argb: Int)

    fun textWidth(text: String): Int

    /** 16x16 icon with count, durability and glint. */
    fun item(ctx: Any, raw: String, x: Int, y: Int)

    /** Just the count and durability bar, for an icon drawn elsewhere. */
    fun itemDecorations(ctx: Any, raw: String, x: Int, y: Int)

    fun tooltip(ctx: Any, raw: String, x: Int, y: Int)

    /**
     * Redraws the inventory in the theme's card and cell glass at real slot positions, so clicks stay native. Call
     * after the overlay panel.
     */
    fun renderInventoryDark(ctx: Any, mouseX: Int, mouseY: Int)

    /** A stack - pair with [popScissor]. */
    fun pushScissor(ctx: Any, x: Int, y: Int, w: Int, h: Int)
    fun popScissor(ctx: Any)
}
