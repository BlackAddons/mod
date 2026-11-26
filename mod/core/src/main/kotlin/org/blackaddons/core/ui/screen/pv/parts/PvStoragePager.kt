package org.blackaddons.core.ui.screen.pv.parts

import org.blackaddons.api.skyblock.profile.InventoryStorage
import org.blackaddons.api.skyblock.profile.ProfileItem
import org.blackaddons.api.ui.layout.*
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.WidgetHost
import org.blackaddons.api.util.I18n
import kotlin.math.ceil
import kotlin.math.floor

internal class PvStoragePager(
    private val widgets: WidgetHost,
    private val titleKey: String,
    private val fallbackTitle: String,
) {

    private var selected = 0

    fun reset() {
        selected = 0
    }

    /** Shows page [index] of the list, when a search result jumps to it. */
    fun select(index: Int) {
        selected = index
    }

    /**
     * The pager for [pages] at [width]: a header, the page selector, then the selected page's grid. [match] marks
     * search hits in the grid.
     */
    fun node(width: Float, pages: List<InventoryStorage>, match: ((ProfileItem) -> Boolean)? = null): LayoutNode {
        val title = I18n.trOr(titleKey, fallbackTitle)
        if (pages.isEmpty()) {
            return column(spacing = Theme.PADDING_SMALL, width = width) {
                sectionHeader(width, title)
                note(width, I18n.trOr("blackaddons.pv.inventory_detail.empty", "No items recorded."))
            }
        }
        val current = page(pages)
        val pageLabel = I18n.trOr("blackaddons.pv.inventory_detail.page", "Page %d").format(current.index)
        val selectorColumns = selectorColumns(width, pages.size)
        val selectorRows = ceil(pages.size / selectorColumns.toDouble()).toInt()
        val selectorWidth = minOf(width, selectorColumns * PAGE_MAX_W + (selectorColumns - 1) * PAGE_GAP)
        val tileWidth = (selectorWidth - (selectorColumns - 1) * PAGE_GAP) / selectorColumns
        val selectorHeight = selectorRows * PAGE_H + (selectorRows - 1) * PAGE_GAP
        val cell = PvInventoryGrid.cell(width, COLUMNS)
        return column(spacing = Theme.PADDING, align = Align.CENTER, width = width) {
            sectionHeader(width, title, pageLabel)
            cell(selectorWidth, selectorHeight) { sx, sy -> drawSelector(sx, sy, pages, selectorColumns, tileWidth) }
            add(PvInventoryGrid.node(COLUMNS, cell, current.slots, match = match))
        }
    }

    private fun page(pages: List<InventoryStorage>): InventoryStorage {
        selected = selected.coerceIn(0, pages.lastIndex.coerceAtLeast(0))
        return pages.getOrElse(selected) { InventoryStorage(1, emptyList()) }
    }

    private fun drawSelector(x: Float, y: Float, pages: List<InventoryStorage>, columns: Int, tileWidth: Float) {
        pages.forEachIndexed { index, storage ->
            val tileX = x + index % columns * (tileWidth + PAGE_GAP)
            val tileY = y + index / columns * (PAGE_H + PAGE_GAP)
            val active = index == selected
            Render.rect(tileX, tileY, tileWidth, PAGE_H, if (active) Theme.SURFACE_ACTIVE else Theme.BACKGROUND, Theme.RADIUS_SMALL)
            if (active) Render.hollowRect(tileX, tileY, tileWidth, PAGE_H, 1f, Theme.ACCENT, Theme.RADIUS_SMALL)
            drawPreview(tileX, tileY, tileWidth, storage, active)
            widgets.clickable(tileX, tileY, tileWidth, PAGE_H) { selected = index }
        }
    }

    private fun drawPreview(x: Float, y: Float, width: Float, storage: InventoryStorage, active: Boolean) {
        val label = storage.index.toString()
        val labelW = Render.textWidth(label, Theme.FONT_SMALL, Fonts.medium)
        val labelX = x + width - PAGE_INSET - labelW
        var previewX = x + PAGE_INSET
        storage.icon?.let {
            Render.itemSpec(it.raw, previewX, centered(y, PAGE_H, PAGE_ICON_SIZE), PAGE_ICON_SIZE, PAGE_ICON_SIZE)
            previewX += PAGE_ICON_SIZE + PAGE_ICON_GAP
        }

        val rows = PvInventoryGrid.rows(storage.slots.size, PREVIEW_COLUMNS).coerceAtMost(PREVIEW_ROWS)
        val availableWidth = labelX - PAGE_LABEL_GAP - previewX
        val cell = minOf(PREVIEW_MAX_CELL, availableWidth / PREVIEW_COLUMNS, (PAGE_H - PREVIEW_Y * 2f) / rows)
        if (cell >= PREVIEW_MIN_CELL) {
            val previewY = centered(y, PAGE_H, rows * cell)
            storage.slots.forEachIndexed { index, raw ->
                val column = index % PREVIEW_COLUMNS
                val row = index / PREVIEW_COLUMNS
                if (row >= PREVIEW_ROWS) return@forEachIndexed
                val color = if (raw == null) Theme.SURFACE else Theme.ACCENT.withAlpha(if (active) 230 else 150)
                Render.rect(previewX + column * cell, previewY + row * cell, cell - PREVIEW_GAP, cell - PREVIEW_GAP, color, 1f)
            }
        }

        Render.text(
            label,
            labelX,
            Render.centerY(y, PAGE_H, Theme.FONT_SMALL, Fonts.medium),
            Theme.FONT_SMALL,
            if (active) Theme.TEXT_PRIMARY else Theme.TEXT_SECONDARY,
            Fonts.medium,
        )
    }

    internal companion object {
        fun selectorColumns(width: Float, pageCount: Int): Int = minOf(
            pageCount.coerceAtLeast(1),
            MAX_PAGE_COLUMNS,
            floor((width + PAGE_GAP) / (PAGE_MIN_W + PAGE_GAP)).toInt().coerceAtLeast(1),
        )

        const val COLUMNS = 9
        const val PAGE_H = 42f
        const val PAGE_GAP = 4f
        const val PAGE_MIN_W = 62f
        const val PAGE_MAX_W = 94f
        const val PAGE_INSET = 5f
        const val PAGE_ICON_SIZE = 12f
        const val PAGE_ICON_GAP = 3f
        const val PAGE_LABEL_GAP = 4f
        const val PREVIEW_COLUMNS = 9
        const val PREVIEW_ROWS = 5
        const val PREVIEW_Y = 4f
        const val PREVIEW_MAX_CELL = 3.5f
        const val PREVIEW_MIN_CELL = 2f
        const val PREVIEW_GAP = 1f
        const val MAX_PAGE_COLUMNS = 9
    }
}
