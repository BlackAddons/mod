package org.blackaddons.core.ui.screen

import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.util.I18n
import org.blackaddons.core.ui.screen.ThemeScreen.Companion.CHIP_H
import org.blackaddons.core.ui.screen.ThemeScreen.Companion.PREVIEW_H
import org.blackaddons.core.ui.screen.ThemeScreen.Companion.STATE_H
import java.awt.Color

/** ThemeScreen's preview: text, marks and state samples in the theme being edited. */

private val tr = I18n.scope("blackaddons.theme_screen")

/** Slots the rest of the screen doesn't show in use. */
internal fun ThemeScreen.drawPreview(x: Float, y: Float, w: Float) {
    Render.rect(x, y, w, PREVIEW_H, Theme.SURFACE.withAlpha(150), Theme.RADIUS_SMALL)
    Render.hollowRect(x, y, w, PREVIEW_H, 1f, Theme.BORDER, Theme.RADIUS_SMALL)
    Render.text(tr("preview", "Preview"), x + Theme.PADDING, y + 6f, TextStyle.SMALL_MUTED)

    val top = y + 24f
    val columnW = (w - Theme.PADDING * 4f) / 3f
    drawTextSamples(x + Theme.PADDING, top)
    drawMarkSamples(x + Theme.PADDING * 2f + columnW, top, columnW)
    drawStateSamples(x + Theme.PADDING * 3f + columnW * 2f, top, columnW)
}

internal fun ThemeScreen.drawTextSamples(x: Float, y: Float) {
    var lineY = y
    listOf(
        tr("sample_primary", "Primary text") to Theme.TEXT_PRIMARY,
        tr("sample_secondary", "Secondary text") to Theme.TEXT_SECONDARY,
        tr("sample_muted", "Muted text") to Theme.TEXT_MUTED,
        tr("sample_disabled", "Disabled text") to Theme.TEXT_DISABLED,
    ).forEach { (text, color) ->
        Render.text(text, x, lineY, Theme.FONT_SMALL, color, Fonts.regular)
        lineY += Theme.FONT_SMALL + 5f
    }
}

internal fun ThemeScreen.drawMarkSamples(x: Float, y: Float, w: Float) {
    chip(tr("sample_accent", "Accent"), x, y, Theme.ACCENT)
    chip(tr("sample_success", "Success"), x, y + CHIP_H + 5f, Theme.SUCCESS)
    chip(tr("sample_danger", "Danger"), x, y + (CHIP_H + 5f) * 2f, Theme.DANGER)

    val trackY = y + (CHIP_H + 5f) * 3f + 3f
    Render.rect(x, trackY, w, 6f, Theme.TRACK, 3f)
    Render.rect(x, trackY, w * 0.6f, 6f, Theme.ACCENT, 3f)
}

/** Hover, active and overlay only show in specific states, so they're shown here explicitly. */
internal fun ThemeScreen.drawStateSamples(x: Float, y: Float, w: Float) {
    listOf(
        tr("state_normal", "Row") to Theme.SURFACE,
        tr("state_hover", "Hovered") to Theme.SURFACE_HOVER,
        tr("state_active", "Active") to Theme.SURFACE_ACTIVE,
        tr("state_dimmed", "Dimmed") to Theme.SURFACE,
    ).forEachIndexed { index, (label, fill) ->
        val rowY = y + index * (STATE_H + 3f)
        Render.rect(x, rowY, w, STATE_H, fill, Theme.RADIUS_SMALL)
        Render.hollowRect(x, rowY, w, STATE_H, 1f, Theme.BORDER, Theme.RADIUS_SMALL)
        if (index == 3) Render.rect(x, rowY, w, STATE_H, Theme.OVERLAY_DIM, Theme.RADIUS_SMALL)
        Render.text(
            label,
            x + Theme.PADDING_SMALL,
            Render.centerY(rowY, STATE_H, TextStyle.SMALL_SECONDARY),
            TextStyle.SMALL_SECONDARY, maxWidth = w - Theme.PADDING,
        )
    }
}

internal fun ThemeScreen.chip(text: String, x: Float, y: Float, fill: Color) {
    val width = Render.textWidth(text, Theme.FONT_SMALL, Fonts.medium) + Theme.PADDING * 2f
    Render.rect(x, y, width, CHIP_H, fill, CHIP_H / 2f)
    Render.text(
        text, x + Theme.PADDING, Render.centerY(y, CHIP_H, Theme.FONT_SMALL, Fonts.medium),
        Theme.FONT_SMALL, Theme.BACKGROUND, Fonts.medium,
    )
}
