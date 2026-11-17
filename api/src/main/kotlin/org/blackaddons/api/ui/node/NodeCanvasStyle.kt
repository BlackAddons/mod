package org.blackaddons.api.ui.node

import org.blackaddons.api.ui.theme.Palette
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.util.color.lerpColor
import java.awt.Color

internal class NodeCanvasStyle {

    var palette: Palette? = null
        private set
    var background: Color = Color.BLACK
        private set
    var body: Color = Color.DARK_GRAY
        private set
    var bodyDim: Color = Color.DARK_GRAY
        private set
    var border: Color = Color.GRAY
        private set
    var accent: Color = Color.WHITE
        private set
    var accentFill: Color = Color.WHITE
        private set
    var accentLine: Color = Color.WHITE
        private set
    var textPrimary: Color = Color.WHITE
        private set
    var textMuted: Color = Color.GRAY
        private set
    var danger: Color = Color.RED
        private set
    var minimapBack: Color = Color.BLACK
        private set
    var minimapNodes: Color = Color.GRAY
        private set
    var fieldFill: Color = Color.BLACK
        private set
    var fieldHover: Color = Color.WHITE
        private set
    private val dimmedHeaders = HashMap<Color, Color>()
    private val wireColors = HashMap<SocketType, Color>()
    private val wireHighlights = HashMap<SocketType, Color>()

    /** True when the theme changed and everything must be redrawn. */
    fun refresh(): Boolean {
        val current = Theme.palette
        if (current === palette) return false
        palette = current
        background = Theme.BACKGROUND
        body = Theme.SURFACE
        bodyDim = Theme.SURFACE.withAlpha(150)
        border = Theme.BORDER
        accent = Theme.ACCENT
        accentFill = Theme.ACCENT.withAlpha(40)
        accentLine = Theme.ACCENT.withAlpha(170)
        textPrimary = Theme.TEXT_PRIMARY
        textMuted = Theme.TEXT_MUTED
        danger = Theme.DANGER
        minimapBack = Theme.BACKGROUND.withAlpha(225)
        minimapNodes = Theme.TEXT_MUTED.withAlpha(170)
        fieldFill = Theme.BACKGROUND.withAlpha(170)
        fieldHover = Theme.TEXT_PRIMARY.withAlpha(24)
        dimmedHeaders.clear()
        wireHighlights.clear()
        return true
    }

    fun headerOf(node: GraphNode): Color =
        if (!node.dimmed) node.headerColor else dimmedHeaders.getOrPut(node.headerColor) { node.headerColor.withAlpha(95) }

    fun wireColor(type: SocketType): Color = wireColors.getOrPut(type) { type.color.withAlpha(215) }

    fun wireHighlight(type: SocketType): Color =
        wireHighlights.getOrPut(type) { lerpColor(type.color, accent, HIGHLIGHT_TINT) }

    private companion object {
        const val HIGHLIGHT_TINT = 0.5f
    }
}
