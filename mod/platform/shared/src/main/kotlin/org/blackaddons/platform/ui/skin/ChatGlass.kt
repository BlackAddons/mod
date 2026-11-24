package org.blackaddons.platform.ui.skin

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.multiplayer.chat.GuiMessage
import org.blackaddons.api.ui.theme.Glass
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.util.color.fade
import org.blackaddons.platform.ui.render.BakedGlass
import kotlin.math.max

/**
 * Chat on glass: a bubble per message, or one card behind every visible line. It replaces only vanilla's fill behind
 * each line, so text, fades, scrolling, the text background opacity option and the other chat features stay. The
 * chat mixins call in. Render thread only.
 */
object ChatGlass {

    @JvmField var enabled = false
    @JvmField var bubbles = true

    private var lines: List<GuiMessage.Line> = emptyList()
    private var scroll = 0
    private var first = true
    private var groupEnded = true
    private var maxAlpha = 1f
    private var lastMaxAlpha = 1f

    /** Before each drawn chat pass. Lines are visited top to bottom; [lines] runs newest first. */
    fun begin(lines: List<GuiMessage.Line>, scroll: Int) {
        this.lines = lines
        this.scroll = scroll
        first = true
        groupEnded = true
        lastMaxAlpha = maxAlpha
        maxAlpha = 0f
    }

    /** In place of vanilla's fill behind [line]; false keeps vanilla's. */
    fun line(g: GuiGraphicsExtractor, x0: Int, y0: Int, x1: Int, y1: Int, color: Int, line: GuiMessage.Line, alpha: Float): Boolean {
        if (!enabled) return false
        maxAlpha = max(maxAlpha, alpha)
        val lineH = y1 - y0
        if (bubbles) {
            if (groupEnded) bubble(g, x0, y0, lineH, color, line)
            groupEnded = line.endOfEntry()
        } else if (first) {
            val index = indexOf(line)
            if (index >= 0 && alpha > 0f) {
                val opacity = (color ushr 24) / alpha * lastMaxAlpha
                val bottom = y1 + (index - scroll) * lineH + SkinGlass.PAD
                val top = y0 - SkinGlass.PAD
                BakedGlass.fill(g, x0, top, x1 - x0, bottom - top, Theme.RADIUS_SMALL, tint(opacity.toInt()))
                val edge = (opacity / OPAQUE * EDGE_ALPHA).coerceIn(0f, 1f)
                BakedGlass.border(g, x0, top, x1 - x0, bottom - top, Theme.RADIUS_SMALL, 1f, fade(Theme.BORDER.rgb, edge))
            }
        }
        first = false
        return true
    }

    /** The chat screen's input box. False keeps vanilla's. */
    fun input(g: GuiGraphicsExtractor, x0: Int, y0: Int, x1: Int, y1: Int): Boolean {
        if (!enabled) return false
        SkinGlass.draw(g, x0, y0, x1 - x0, y1 - y0, Glass.BUTTON, hover = 1f)
        return true
    }

    /** Before the command suggestions draw: one popup behind the list and its scroll edges. */
    fun suggestions(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) {
        if (enabled) SkinGlass.draw(g, x - 1, y - 2, w + 2, h + 4, Glass.TOOLTIP)
    }

    /** Vanilla's row fills under the suggestions, skipped on the popup; the white scroll dots stay. */
    fun suggestionFill(color: Int): Boolean = !enabled || color == -1

    /** Selected and unselected suggestions in the theme's colors. */
    fun suggestionText(color: Int): Int = when {
        !enabled -> color
        color == SUGGESTION_SELECTED -> Theme.ACCENT.rgb
        color == SUGGESTION -> Theme.TEXT_SECONDARY.rgb
        else -> color
    }

    /** The command usage hint's line fills. False keeps vanilla's. */
    fun usage(g: GuiGraphicsExtractor, x0: Int, y0: Int, x1: Int, y1: Int): Boolean {
        if (!enabled) return false
        SkinGlass.draw(g, x0 - 1, y0, x1 - x0 + 2, y1 - y0, Glass.TOOLTIP)
        return true
    }

    /* From a message's top line down to its last one, as wide as its widest line. */
    private fun bubble(g: GuiGraphicsExtractor, x0: Int, y0: Int, lineH: Int, color: Int, top: GuiMessage.Line) {
        var i = indexOf(top)
        if (i < 0) return
        val font = Minecraft.getInstance().font
        var count = 0
        var width = 0
        while (i >= scroll) {
            val l = lines[i]
            width = max(width, font.width(l.content()))
            count++
            if (l.endOfEntry()) break
            i--
        }
        BakedGlass.fill(g, x0, y0, width + PAD * 2, count * lineH - GAP, Theme.RADIUS_SMALL, tint(color ushr 24))
    }

    /* Visible lines sit just above the scroll position, so this is a short scan. */
    private fun indexOf(line: GuiMessage.Line): Int {
        for (i in scroll until lines.size) if (lines[i] === line) return i
        return -1
    }

    /* Vanilla's alpha (chat fade times the text background opacity option) on the theme's background. */
    private fun tint(alpha: Int): Int = (alpha.coerceIn(0, 255) shl 24) or (Theme.BACKGROUND.rgb and 0xFFFFFF)

    /* Vanilla's fill starts 4 left of the text; the bubble mirrors that on the right. */
    private const val SUGGESTION_SELECTED = -256
    private const val SUGGESTION = -5592406
    private const val PAD = 4
    private const val OPAQUE = 255f
    private const val EDGE_ALPHA = 0.6f
    private const val GAP = 1
}
