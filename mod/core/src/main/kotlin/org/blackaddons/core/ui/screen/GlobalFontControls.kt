package org.blackaddons.core.ui.screen

import org.blackaddons.api.ui.layout.column
import org.blackaddons.api.ui.layout.columnWidth
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.Tooltip
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.ProgressBar
import org.blackaddons.api.ui.widget.Slider
import org.blackaddons.api.ui.widget.Swatch
import org.blackaddons.core.font.GlobalFontDraft
import org.blackaddons.core.ui.screen.GlobalFontScreen.Companion.CONTROL_ROW_H
import org.blackaddons.core.ui.screen.GlobalFontScreen.Companion.LABEL_SIZE
import org.blackaddons.core.ui.screen.GlobalFontScreen.Companion.NOTE_H
import org.blackaddons.core.ui.screen.GlobalFontScreen.Companion.PROGRESS_H
import org.blackaddons.core.ui.screen.GlobalFontScreen.Tab
import java.awt.Color

/* GlobalFontScreen's right column: style toggles, sliders, colors and notes. */

internal fun GlobalFontScreen.drawControls(x: Float, y: Float, w: Float, h: Float, mouseX: Float, mouseY: Float) {
    val selectedLabel = if (downloading) tr("downloading", "Downloading...") else selected.label
    val fullSelected = tr("selected", "Selected: %s", selectedLabel)
    selectedText.text = Render.ellipsize(fullSelected, w, Theme.FONT_SMALL, Fonts.medium)
    selectedText.render(x, y + 1f)
    Tooltip.showOver(x, y, w, 13f, fullSelected.takeIf { it != selectedText.text })
    val progress = transferProgress
    val fullStatus = operationError ?: progress?.label ?: native?.status.orEmpty()
    statusText.text = Render.ellipsize(fullStatus, w, LABEL_SIZE, Fonts.regular)
    statusText.color = if (operationError != null) Theme.DANGER else Theme.TEXT_MUTED
    statusText.render(x, y + 15f)
    Tooltip.showOver(x, y + 13f, w, 13f, fullStatus.takeIf { it != statusText.text })
    when {
        progress?.fraction != null -> ProgressBar.draw(x, y + 28f, w, PROGRESS_H, progress.fraction)
        progress != null || native?.applying == true -> ProgressBar.drawIndeterminate(x, y + 28f, w, PROGRESS_H)
    }

    val gap = 12f
    val cellW = (w - gap) / 2f
    shadowXSlider.enabled = draft.shadowEnabled
    shadowYSlider.enabled = draft.shadowEnabled
    outlineSlider.enabled = draft.outlineEnabled
    shadowColorButton.enabled = draft.shadowEnabled
    outlineColorButton.enabled = draft.outlineEnabled
    val buttonGap = 4f
    val toggles = if (tab == Tab.HUD) listOf(boldButton, italicButton) else listOf(boldButton, italicButton, shadowButton, outlineButton)
    val buttonW = columnWidth(w, toggles.size, buttonGap)
    boldButton.label = toggleLabel(tr("bold", "Bold"), draft.forceBold)
    italicButton.label = toggleLabel(tr("italic", "Italic"), draft.forceItalic)
    shadowButton.label = toggleLabel(tr("shadow", "Shadow"), draft.shadowEnabled)
    outlineButton.label = toggleLabel(tr("outline", "Outline"), draft.outlineEnabled)
    boldButton.accent = draft.forceBold
    italicButton.accent = draft.forceItalic
    shadowButton.accent = draft.shadowEnabled
    outlineButton.accent = draft.outlineEnabled
    val fixedHeight = when {
        !tuning -> 39f + NOTE_H
        tab == Tab.HUD -> 39f + CONTROL_ROW_H * 2f + 22f + 8f
        tab == Tab.MINECRAFT -> 39f + CONTROL_ROW_H * 5f + 22f + 6f + 22f + 8f
        else -> 39f + CONTROL_ROW_H * 4f + 22f + 6f + 22f + 8f
    }
    val previewH = (h - fixedHeight).coerceAtLeast(48f)
    column(width = w, height = h) {
        spacer(height = 39f)
        if (!tuning) {
            cell(w, NOTE_H) { cx, cy -> drawNote(tr("vanilla_note", "Minecraft's own font, unchanged."), cx, cy, w) }
            cell(w, previewH) { cx, cy -> drawPreview(cx, cy, w, previewH) }
            return@column
        }
        when (tab) {
            Tab.HUD -> sliderRow(
                tr("size", "Size"), draft.rasterSize, rasterSlider,
                tr("letter_spacing", "Letter spacing"), draft.letterSpacing, spacingSlider, w, mouseX, mouseY
            )
            Tab.MINECRAFT -> {
                sliderRow(
                    tr("raster_size", "Raster size"), draft.rasterSize, rasterSlider,
                    tr("letter_spacing", "Letter spacing"), draft.letterSpacing, spacingSlider, w, mouseX, mouseY
                )
                sliderRow(
                    tr("oversampling_2d", "2D oversampling"), draft.oversample2d, oversample2dSlider,
                    tr("oversampling_world", "World oversampling"), draft.oversampleWorld, oversampleWorldSlider,
                    w, mouseX, mouseY, "×",
                    leftShown = if (draft.oversample2d == GlobalFontDraft.AUTO_OVERSAMPLE) {
                        tr("oversampling_auto", "Auto")
                    } else {
                        format(draft.oversample2d)
                    }
                )
            }
            Tab.WORLD -> sliderRow(
                tr("raster_size", "Raster size"), draft.rasterSize, rasterSlider,
                tr("oversampling_world", "World oversampling"), draft.oversampleWorld, oversampleWorldSlider,
                w, mouseX, mouseY, "×"
            )
        }
        if (tab == Tab.WORLD) {
            sliderRow(
                tr("letter_spacing", "Letter spacing"), draft.letterSpacing, spacingSlider,
                tr("outline_width", "Outline width"), draft.outlineWidth, outlineSlider, w, mouseX, mouseY
            )
        }
        sliderRow(
            tr("glyph_x_offset", "Glyph X offset"), draft.shiftX, shiftXSlider,
            tr("glyph_y_offset", "Glyph Y offset"), draft.shiftY, shiftYSlider, w, mouseX, mouseY
        )
        if (tab == Tab.MINECRAFT) {
            singleSliderRow(tr("outline_width", "Outline width"), draft.outlineWidth, outlineSlider, w, mouseX, mouseY)
        }
        if (tab != Tab.HUD) {
            sliderRow(
                tr("shadow_x", "Shadow X"), draft.shadowX, shadowXSlider,
                tr("shadow_y", "Shadow Y"), draft.shadowY, shadowYSlider, w, mouseX, mouseY
            )
        }
        row(spacing = buttonGap, width = w, height = 22f) {
            toggles.forEach { button ->
                cell(buttonW, 22f) { cx, cy -> button.render(cx, cy, buttonW, 22f, mouseX, mouseY) }
            }
        }
        if (tab != Tab.HUD) {
            spacer(height = 6f)
            row(spacing = gap, width = w, height = 22f) {
                cell(cellW, 22f) { cx, cy ->
                    shadowColorButton.render(cx, cy, cellW, 22f, mouseX, mouseY)
                    drawSwatch(cx + cellW - 22f, cy + 4f, draft.shadowColor, shadowColorButton.enabled)
                }
                cell(cellW, 22f) { cx, cy ->
                    outlineColorButton.render(cx, cy, cellW, 22f, mouseX, mouseY)
                    drawSwatch(cx + cellW - 22f, cy + 4f, draft.outlineColor, outlineColorButton.enabled)
                }
            }
        }
        spacer(height = 8f)
        cell(w, previewH) { cx, cy -> drawPreview(cx, cy, w, previewH) }
    }.draw(x, y)
}

internal fun GlobalFontScreen.drawNote(note: String, x: Float, y: Float, w: Float) {
    Render.text(note, x, y, LABEL_SIZE, Theme.TEXT_MUTED, Fonts.regular, maxWidth = w)
}

internal fun GlobalFontScreen.drawCompactSlider(
    label: String,
    value: Double,
    slider: Slider,
    x: Float,
    y: Float,
    w: Float,
    mouseX: Float,
    mouseY: Float,
    suffix: String = "",
    shown: String = format(value),
) {
    Render.text("$label: $shown$suffix", x, y, LABEL_SIZE, Theme.TEXT_SECONDARY, Fonts.regular)
    slider.render(x, y + 11f, w, 14f, mouseX, mouseY)
}

internal fun GlobalFontScreen.drawSwatch(x: Float, y: Float, color: Color, enabled: Boolean) =
    Swatch.draw(x, y, 18f, 14f, if (enabled) color else color.withAlpha(55))
