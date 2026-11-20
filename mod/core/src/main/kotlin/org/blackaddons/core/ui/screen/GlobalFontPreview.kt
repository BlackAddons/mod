package org.blackaddons.core.ui.screen

import org.blackaddons.api.ui.render.Font
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.core.ui.screen.GlobalFontScreen.Companion.LABEL_SIZE
import org.blackaddons.core.ui.screen.GlobalFontScreen.Companion.OUTLINE
import org.blackaddons.core.ui.screen.GlobalFontScreen.Companion.PREVIEW_LINES
import java.awt.Color

/* GlobalFontScreen's live preview of the picked font and tuning. */

internal fun GlobalFontScreen.drawPreview(x: Float, y: Float, w: Float, h: Float) {
    Render.rect(x, y, w, h, Theme.SURFACE.withAlpha(150), Theme.RADIUS_SMALL)
    val pulse = selectionPulse.get()
    if (pulse > 0f) Render.hollowRect(x, y, w, h, 1f, Theme.ACCENT.withAlpha((pulse * 150f).toInt()), Theme.RADIUS_SMALL)
    Render.text(tr("preview", "Preview"), x + 8f, y + 6f, LABEL_SIZE, Theme.TEXT_MUTED, Fonts.regular)
    val font = previewFont()
    val size = (11f * (look.rasterSize / 11.0)).toFloat().coerceIn(8f, 17f)
    val reveal = previewReveal.get()
    var lineY = y + 20f + look.shiftY.toFloat() + (1f - reveal) * 5f
    val textX = x + 9f + look.shiftX.toFloat()
    Render.pushScissor(x + 4f, y + 16f, w - 8f, h - 20f)
    for (line in PREVIEW_LINES) {
        drawPreviewLine(line, textX, lineY, size, font)
        lineY += size * font.scale * 1.35f
    }
    Render.popScissor()
}

internal fun GlobalFontScreen.drawPreviewLine(text: String, x: Float, y: Float, size: Float, font: Font) {
    if (look.outlineEnabled) {
        val width = look.outlineWidth.toFloat()
        for ((dx, dy) in OUTLINE) drawSpaced(text, x + dx * width, y + dy * width, size, look.outlineColor, font)
    }
    if (look.shadowEnabled) {
        drawSpaced(text, x + look.shadowX.toFloat(), y + look.shadowY.toFloat(), size, look.shadowColor, font)
    }
    drawSpaced(text, x, y, size, Theme.TEXT_PRIMARY, font)
}

internal fun GlobalFontScreen.drawSpaced(text: String, x: Float, y: Float, size: Float, color: Color, font: Font) {
    if (look.forceItalic) {
        Render.push()
        Render.translate(x, y)
        Render.shearX(-0.2f)
        drawSpacedRaw(text, 0f, 0f, size, color, font)
        Render.pop()
        return
    }
    drawSpacedRaw(text, x, y, size, color, font)
}

internal fun GlobalFontScreen.drawSpacedRaw(text: String, x: Float, y: Float, size: Float, color: Color, font: Font) {
    val spacing = look.letterSpacing
    if (spacing == 0.0) {
        Render.text(text, x, y, size, color, font)
        return
    }
    var cursor = x
    for (codePoint in text.codePoints().toArray()) {
        val glyph = String(Character.toChars(codePoint))
        Render.text(glyph, cursor, y, size, color, font)
        cursor += Render.textWidth(glyph, size, font) + spacing.toFloat()
    }
}
