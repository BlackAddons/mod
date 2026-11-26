package org.blackaddons.api.hud

import org.blackaddons.api.chat.ChatMessage
import org.blackaddons.api.feature.settings.impl.ColorSetting
import org.blackaddons.api.ui.layout.*
import org.blackaddons.api.ui.render.Font
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Theme
import java.awt.Color

/**
 * HUD versions of the layout primitives, using [HudFontContext] and [Theme.FONT_SMALL]. The framework
 * measures the tree, so editor bounds always match what was drawn.
 */

/** Current element's font unless [font] is given. */
fun LayoutBuilder.hudText(
    text: String,
    color: Color,
    size: Float = Theme.FONT_SMALL,
    font: Font = HudFontContext.font,
    shadow: Boolean = false,
) = add(hudTextNode(text, color, size, font, shadow))

/** Honors the [ColorSetting]'s chroma mode; measured like [hudText]. */
fun LayoutBuilder.hudChromaText(
    text: String,
    color: ColorSetting,
    size: Float = Theme.FONT_SMALL,
    font: Font = HudFontContext.font,
) = add(
    Cell(Render.textWidth(text, size, font), Render.textHeight(size, font)) { x, y ->
        Render.chromaText(text, x, y, size, color, font)
    },
)

/** For composing a tree outside a builder. */
fun hudTextNode(
    text: String,
    color: Color,
    size: Float = Theme.FONT_SMALL,
    font: Font = HudFontContext.font,
    shadow: Boolean = false,
): LayoutNode = Cell(Render.textWidth(text, size, font), Render.textHeight(size, font)) { x, y ->
    if (shadow) {
        Render.textShadow(text, x, y, size, color, font)
    } else {
        Render.text(text, x, y, size, color, font)
    }
}

/** [hudTextNode] that honors `§`/`&` color codes; uncolored runs use [color]. */
fun hudFormattedTextNode(
    text: String,
    color: Color,
    size: Float = Theme.FONT_SMALL,
    font: Font = HudFontContext.font,
    shadow: Boolean = false,
): LayoutNode {
    val spans = ChatMessage.legacy(text).spans
    val width = spans.fold(0f) { w, span -> w + Render.textWidth(span.text, size, font) }
    return Cell(width, Render.textHeight(size, font)) { x, y ->
        var cx = x
        for (span in spans) {
            val c = span.color ?: color
            if (shadow) Render.textShadow(span.text, cx, y, size, c, font) else Render.text(span.text, cx, y, size, c, font)
            cx += Render.textWidth(span.text, size, font)
        }
    }
}

fun hudPanel(
    background: Color,
    border: Color? = null,
    radius: Float = 0f,
    padding: Float = PANEL_PADDING,
    spacing: Float = PANEL_LINE_GAP,
    content: LayoutBuilder.() -> Unit,
): LayoutNode {
    val boxed = column(spacing = spacing, block = content)
        .padded(padding)
        .background(background, radius)
    return if (border != null) boxed.bordered(border, radius) else boxed
}

const val PANEL_PADDING = 4f

const val PANEL_LINE_GAP = 2f
