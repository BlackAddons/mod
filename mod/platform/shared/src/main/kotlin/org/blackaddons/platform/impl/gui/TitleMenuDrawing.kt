package org.blackaddons.platform.impl.gui

import net.minecraft.SharedConstants
import net.minecraft.client.gui.screens.TitleScreen
import org.blackaddons.api.Platform
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.util.color.lerpColor
import org.blackaddons.platform.impl.gui.TitleMenu.Slot
import java.awt.Color

// TitleMenu's painting: the title, text and icon rows, tooltips and legal links.

internal fun TitleMenu.drawTitle(screen: TitleScreen) {
    val texts = texts()
    val firstY = texts.firstOrNull()?.widget?.y ?: (screen.height / 2)
    val cx = screen.width / 2f
    val versionHeight = Render.textHeight(sf(VERSION_SIZE), Fonts.regular)
    val titleHeight = Render.textHeight(sf(TITLE_SIZE), Fonts.bold)
    val titleY = firstY - si(TITLE_GAP) - versionHeight - titleHeight
    Render.image(LOGO_PATH, cx - sf(LOGO_SIZE) / 2, titleY - sf(LOGO_GAP) - sf(LOGO_SIZE), sf(LOGO_SIZE), sf(LOGO_SIZE))
    val name = "BlackAddons"
    val x = cx - Render.textWidth(name, sf(TITLE_SIZE), Fonts.bold) / 2
    outlined(name, x, titleY)
    Render.textCentered(Platform.blackaddonsVersion, cx, titleY + titleHeight, sf(VERSION_SIZE), Theme.TEXT_MUTED, Fonts.regular)
}

// A dark outline plus a soft shadow under it, so the gradient looks raised instead of printed on the nebula.
internal fun TitleMenu.outlined(text: String, x: Float, y: Float) {
    Render.text(text, x, y + sf(SHADOW_DROP), sf(TITLE_SIZE), SHADOW, Fonts.bold)
    OUTLINE_OFFSETS.forEach { (dx, dy) -> Render.text(text, x + dx * sf(OUTLINE), y + dy * sf(OUTLINE), sf(TITLE_SIZE), CONTOUR, Fonts.bold) }
    Render.textGradient(text, x, y, sf(TITLE_SIZE), Theme.BRAND_FROM, Theme.BRAND_TO, Fonts.bold)
}

internal fun TitleMenu.drawText(slot: Slot, hover: Float) {
    val w = slot.widget
    val enabled = w.active
    val color = when {
        !enabled -> Theme.TEXT_DISABLED
        else -> lerpColor(Theme.TEXT_PRIMARY, Theme.ACCENT, hover)
    }
    var x = w.x + sf(ITEM_PAD)
    slot.icon?.let {
        Render.icon(it, x, w.y + (si(ITEM_HEIGHT) - sf(ITEM_ICON)) / 2, sf(ITEM_ICON), color)
        x += sf(ITEM_ICON) + sf(ITEM_PAD) / 2
    }
    val textY = Render.centerY(w.y, si(ITEM_HEIGHT), sf(ITEM_SIZE), Fonts.medium)
    Render.text(slot.label, x, textY, sf(ITEM_SIZE), color, Fonts.medium)
    // An underline under the word that fills from its first letter to its last as the hover comes in.
    if (hover > 0.01f) {
        val textWidth = Render.textWidth(slot.label, sf(ITEM_SIZE), Fonts.medium)
        val lineY = textY + Render.textHeight(sf(ITEM_SIZE), Fonts.medium)
        Render.rect(x, lineY, textWidth * hover, sf(UNDERLINE), Theme.ACCENT, radius = sf(UNDERLINE) / 2)
    }
}

internal fun TitleMenu.drawIcon(slot: Slot, hover: Float) {
    val w = slot.widget
    val accent = accentOf(slot)
    val fill = lerpColor(Theme.SURFACE.withAlpha(150), accent.withAlpha(70), hover)
    Render.rect(w.x, w.y, si(CELL), si(CELL), fill, radius = sf(Theme.RADIUS_SMALL))
    val color = if (w.active) lerpColor(Theme.TEXT_SECONDARY, accent, hover) else Theme.TEXT_DISABLED
    slot.icon?.let { Render.icon(it, w.x + (si(CELL) - sf(CELL_ICON)) / 2, w.y + (si(CELL) - sf(CELL_ICON)) / 2, sf(CELL_ICON), color) }
}

internal fun TitleMenu.accentOf(slot: Slot): Color = if (slot.danger) Theme.DANGER else Theme.ACCENT

internal fun TitleMenu.drawTooltip(slot: Slot) {
    val w = slot.widget
    val text = slot.label.trimEnd('.')
    val textWidth = Render.textWidth(text, sf(LEGAL_TEXT), Fonts.regular)
    val padX = sf(TOOLTIP_PAD_X)
    val padY = sf(TOOLTIP_PAD_Y)
    val height = Render.textHeight(sf(LEGAL_TEXT), Fonts.regular) + padY * 2
    val x = w.x + si(CELL) / 2f - textWidth / 2 - padX
    val y = w.y - height - sf(TOOLTIP_GAP)
    Render.rect(x, y, textWidth + padX * 2, height, Theme.SURFACE.withAlpha(230), radius = sf(Theme.RADIUS_SMALL))
    Render.text(text, x + padX, y + padY, sf(LEGAL_TEXT), Theme.TEXT_PRIMARY, Fonts.regular)
}

internal fun TitleMenu.drawLegalToggle(slot: Slot, hover: Float) {
    val w = slot.widget
    val color = lerpColor(Theme.TEXT_MUTED, Theme.TEXT_PRIMARY, maxOf(hover, if (legalOpen) 1f else 0f))
    slot.icon?.let { Render.icon(it, w.x, w.y, si(LEGAL_SIZE), color) }
}

internal fun TitleMenu.drawLegal(slot: Slot, hover: Float) {
    val w = slot.widget
    val version = "Minecraft ${SharedConstants.getCurrentVersion().name()}"
    val color = lerpColor(Theme.TEXT_MUTED, Theme.TEXT_PRIMARY, hover)
    val y = Render.centerY(w.y, w.height, sf(LEGAL_TEXT), Fonts.regular)
    Render.text(version, w.x, y - Render.textHeight(sf(LEGAL_TEXT), Fonts.regular) - sf(LEGAL_LINE_GAP), sf(LEGAL_TEXT), Theme.TEXT_MUTED, Fonts.regular)
    Render.text(slot.label, w.x, y, sf(LEGAL_TEXT), color, Fonts.regular)
}
