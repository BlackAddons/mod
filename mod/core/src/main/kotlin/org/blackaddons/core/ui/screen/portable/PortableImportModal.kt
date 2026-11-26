package org.blackaddons.core.ui.screen.portable

import org.blackaddons.api.ui.icon.LucideIcons
import org.blackaddons.api.ui.layout.centered
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.ModalPanel
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.ScrollableList
import org.blackaddons.api.ui.widget.Swatch
import org.blackaddons.core.config.portable.DiffRow
import org.blackaddons.core.config.portable.DiffValue
import org.blackaddons.core.config.portable.ImportPreview
import org.blackaddons.core.config.portable.PortableImports
import java.awt.Color

/** Previews what a copied config would change. Grows to [MAX_ROWS] rows, then scrolls. */
internal class PortableImportModal<T>(
    title: String,
    private val preview: ImportPreview<T>,
    private val onApplied: (T) -> Unit,
) : ModalPanel(title) {

    override val panelWidth: Float get() = fitWidth(460f, margin = 24f)
    override val panelHeight: Float
        get() = minOf(heightFor(contentHeight(panelWidth - Theme.PADDING_LARGE * 2f)), UiManager.screenHeight - 24f).coerceAtLeast(0f)

    private val rows = widgets.add(ScrollableList<DiffRow>().apply {
        rowHeight = 18f
        rowGap = 1f
        drawBackground = false
        drawRow = { row, _, x, y, w -> drawRow(row, x, y, w) }
    })
    private val cancelButton = widgets.add(Button(PortableImports.tr("cancel", "Cancel")) { UiManager.pop() })
    private val importButton = widgets.add(Button(PortableImports.tr("import", "Import")) { accept() }.apply {
        accent = true
        enabled = preview is ImportPreview.Ready && preview.canApply
    })

    override fun renderContent(mouseX: Float, mouseY: Float) {
        when (preview) {
            is ImportPreview.Failed -> {
                Render.icon(LucideIcons.circleX, contentX, contentY, STATUS_ICON, Theme.DANGER)
                val textX = contentX + STATUS_ICON + Theme.PADDING_SMALL
                paragraph(preview.message, textX, contentY, contentX + contentW - textX)
            }
            is ImportPreview.Ready -> renderReady(preview)
        }
        footer(mouseX, mouseY, cancelButton, importButton)
    }

    private fun renderReady(ready: ImportPreview.Ready<T>) {
        Render.text(
            ready.headline,
            contentX, contentY, TextStyle.BODY_PRIMARY_MEDIUM, maxWidth = contentW,
        )
        var y = contentY + Theme.FONT_BODY + Theme.PADDING
        paragraph(ready.note, contentX, y, contentW)
        y += noteHeight(ready.note, contentW) + Theme.PADDING

        if (ready.rows.isEmpty()) {
            drawSwatches(ready.swatches, contentX, y, contentW)
            return
        }
        val boxH = (footerTop - ModalPanel.FOOTER_GAP - y).coerceAtLeast(0f)
        Render.rect(contentX, y, contentW, boxH, Theme.SURFACE.withAlpha(150), Theme.RADIUS_SMALL)
        Render.hollowRect(contentX, y, contentW, boxH, 1f, Theme.BORDER, Theme.RADIUS_SMALL)
        rows.items = ready.rows
        rows.render(contentX + BOX_PAD, y + BOX_PAD, contentW - BOX_PAD * 2f, boxH - BOX_PAD * 2f)
    }

    private fun contentHeight(w: Float): Float = when (preview) {
        is ImportPreview.Failed -> maxOf(noteHeight(preview.message, w - STATUS_ICON - Theme.PADDING_SMALL), STATUS_ICON)
        is ImportPreview.Ready -> {
            val body = when {
                preview.rows.isNotEmpty() ->
                    preview.rows.size.coerceAtMost(MAX_ROWS) * (rows.rowHeight + rows.rowGap) + BOX_PAD * 2f
                preview.swatches.isNotEmpty() -> swatchSize(preview.swatches.size, w)
                else -> 0f
            }
            Theme.FONT_BODY + Theme.PADDING + noteHeight(preview.note, w) + Theme.PADDING + body
        }
    }

    private fun noteHeight(text: String, w: Float): Float =
        Render.wrappedTextHeight(text, w, Theme.FONT_SMALL, Fonts.regular, LINE_STEP)

    private fun swatchSize(count: Int, w: Float) = ((w - (count - 1) * SWATCH_GAP) / count).coerceAtMost(22f)

    private fun drawSwatches(colors: List<Color>, x: Float, y: Float, w: Float) {
        if (colors.isEmpty()) return
        val size = swatchSize(colors.size, w)
        colors.forEachIndexed { index, color ->
            val cx = x + index * (size + SWATCH_GAP)
            Swatch.draw(cx, y, size, size, color)
        }
    }

    private fun drawRow(row: DiffRow, x: Float, y: Float, w: Float) {
        val textY = Render.centerY(y, rows.rowHeight, Theme.FONT_SMALL, Fonts.regular)
        val labelW = w * LABEL_SHARE - Theme.PADDING
        Render.text(
            row.label,
            x + 4f, textY, TextStyle.SMALL_SECONDARY, maxWidth = labelW,
        )

        val half = (w * (1f - LABEL_SHARE) - ARROW_W) / 2f
        var cx = x + w * LABEL_SHARE
        drawValue(row.before, cx, y, half, Theme.TEXT_MUTED)
        cx += half
        Render.text("→", cx + 4f, textY, TextStyle.SMALL_MUTED)
        cx += ARROW_W
        drawValue(row.after, cx, y, half, Theme.TEXT_PRIMARY)
    }

    private fun drawValue(value: DiffValue, x: Float, y: Float, w: Float, textColor: Color) {
        var textX = x
        value.color?.let {
            Render.rect(x, centered(y, rows.rowHeight, SWATCH), SWATCH, SWATCH, it, 2f)
            textX += SWATCH + 4f
        }
        Render.text(
            value.text, textX, Render.centerY(y, rows.rowHeight, Theme.FONT_SMALL, Fonts.regular),
            Theme.FONT_SMALL, textColor, Fonts.regular, maxWidth = x + w - textX,
        )
    }

    private fun paragraph(text: String, x: Float, y: Float, w: Float) =
        Render.wrappedText(text, x, y, w, TextStyle.SMALL_SECONDARY, LINE_STEP)

    private fun accept() {
        val ready = preview as? ImportPreview.Ready ?: return
        val applied = ready.apply() ?: return
        UiManager.pop()
        onApplied(applied)
    }

    private companion object {
        const val LINE_STEP = 1.45f
        const val LABEL_SHARE = 0.4f
        const val ARROW_W = 22f
        const val SWATCH = 12f
        const val SWATCH_GAP = 3f
        const val STATUS_ICON = 13f
        const val BOX_PAD = 4f
        const val MAX_ROWS = 10
    }
}
