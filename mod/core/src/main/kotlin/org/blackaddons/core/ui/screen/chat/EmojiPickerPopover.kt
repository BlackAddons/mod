package org.blackaddons.core.ui.screen.chat

import org.blackaddons.api.ui.render.Image
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.Popover
import org.blackaddons.api.ui.screen.Tooltip
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.Scroll
import org.blackaddons.api.ui.widget.text.TextInput
import org.blackaddons.api.util.I18n
import org.blackaddons.core.emoji.CustomEmojiStore
import org.blackaddons.core.emoji.Emoji

private val tr = I18n.scope("blackaddons.bachat.emoji")

/** Discord emoji by category plus seen custom emoji. A pick returns text to insert (emoji or `<:name:id>`) and closes. */
internal class EmojiPickerPopover(
    private val anchorX: Float,
    private val anchorY: Float,
    private val onPick: (String) -> Unit,
) : Popover() {

    private data class Cell(val title: String, val insert: String, val glyph: Int?, val customId: String?)

    private val search = widgets.add(TextInput(placeholder = tr("search", "Find an emoji"), maxLength = 40).apply {
        focused = true
        onEscape = { UiManager.pop() }
    })
    private val scroll = widgets.add(Scroll())

    private var tab: String = if (CustomEmojiStore.known.isNotEmpty()) CUSTOM else Emoji.categories.first().id

    override fun render(mouseX: Float, mouseY: Float, deltaTicks: Float) {
        val w = COLUMNS * CELL + SCROLL_GUTTER + Theme.PADDING * 2f
        val h = HEIGHT
        val x = (anchorX - w + CELL).coerceIn(MARGIN, maxOf(MARGIN, UiManager.screenWidth - w - MARGIN))
        val y = (anchorY - h - GAP).coerceAtLeast(MARGIN)
        card.set(x, y, w, h)
        drawCard { drawContent(x, y, w, h, mouseX, mouseY) }
    }

    private fun drawContent(x: Float, y: Float, w: Float, h: Float, mouseX: Float, mouseY: Float) {
        val innerX = x + Theme.PADDING
        val innerW = w - Theme.PADDING * 2f
        var cursor = y + Theme.PADDING
        search.render(innerX, cursor, innerW, FIELD_H, mouseX, mouseY)
        cursor += FIELD_H + Theme.PADDING_SMALL

        val query = search.text.trim().lowercase().replace(":", "")
        if (query.isEmpty()) {
            drawTabs(innerX, cursor, mouseX, mouseY)
            cursor += CELL + Theme.PADDING_SMALL
        }

        val cells = cells(query)
        val gridH = y + h - Theme.PADDING - cursor
        val rows = (cells.size + COLUMNS - 1) / COLUMNS
        scroll.render(innerX, cursor, innerW, gridH, rows * CELL) { offset ->
            cells.forEachIndexed { i, cell ->
                val cx = innerX + i % COLUMNS * CELL
                val cy = cursor + i / COLUMNS * CELL - offset
                if (cy + CELL < cursor || cy > cursor + gridH) return@forEachIndexed
                val hovered = widgets.clickable(cx, cy, CELL, CELL) { pick(cell) }
                if (hovered) {
                    Render.rect(cx, cy, CELL, CELL, Theme.SURFACE_HOVER, Theme.RADIUS_SMALL)
                    Tooltip.show(cell.title)
                }
                drawCell(cell, cx + INSET, cy + INSET, CELL - INSET * 2f)
            }
        }

        if (cells.isEmpty()) {
            val empty = if (tab == CUSTOM && query.isEmpty()) tr("no_custom", "No custom emoji yet") else tr("none", "Nothing matches")
            Render.textCentered(empty, x + w / 2f, cursor + Theme.PADDING, TextStyle.SMALL_MUTED)
        }
    }

    private fun drawTabs(x: Float, y: Float, mouseX: Float, mouseY: Float) {
        var cx = x
        val tabs = buildList {
            if (CustomEmojiStore.known.isNotEmpty()) add(CUSTOM to null)
            Emoji.categories.forEach { add(it.id to it.emoji.firstOrNull()?.glyph) }
        }
        for ((id, glyph) in tabs) {
            val active = id == tab
            val hovered = widgets.clickable(cx, y, CELL, CELL) {
                tab = id
                scroll.reset()
            }
            if (active || hovered) Render.rect(cx, y, CELL, CELL, if (active) Theme.ACCENT_SOFT else Theme.SURFACE_HOVER, Theme.RADIUS_SMALL)
            if (glyph != null) drawGlyph(glyph, cx + INSET, y + INSET, CELL - INSET * 2f)
            else CustomEmojiStore.known.firstOrNull()?.let { drawCustom(it.id, cx + INSET, y + INSET, CELL - INSET * 2f) }
            if (hovered) Tooltip.show(Emoji.categories.firstOrNull { it.id == id }?.label ?: tr("custom", "Custom"))
            cx += CELL
        }
    }

    private fun cells(query: String): List<Cell> {
        val custom = CustomEmojiStore.known
            .filter { query.isEmpty() || query in it.name.lowercase() }
            .map { Cell(":${it.name}:", it.token, null, it.id) }
        val unicode = (if (query.isEmpty()) Emoji.categories.filter { it.id == tab } else Emoji.categories)
            .flatMap { it.emoji }
            .filter { query.isEmpty() || it.names.any { name -> query in name } }
            .map { Cell(":${it.names.first()}:", Emoji.glyphText(it.glyph), it.glyph, null) }

        return when {
            query.isNotEmpty() -> (custom + unicode).take(MAX_RESULTS)
            tab == CUSTOM -> custom
            else -> unicode
        }
    }

    private fun drawCell(cell: Cell, x: Float, y: Float, size: Float) {
        when {
            cell.glyph != null -> drawGlyph(cell.glyph, x, y, size)
            cell.customId != null -> drawCustom(cell.customId, x, y, size)
        }
    }

    private fun drawGlyph(glyph: Int, x: Float, y: Float, size: Float) {
        val (sheet, sx, sy, cell) = Emoji.cellOf(glyph)
        val sheetSize = Emoji.sheetSize()
        Render.image(Image(Emoji.sheetPath(sheet)), x, y, size, size, sx, sy, cell, cell, sheetSize, sheetSize)
    }

    private fun drawCustom(id: String, x: Float, y: Float, size: Float) {
        CustomEmojiStore.source(id)?.let { Render.image(it, x, y, size, size) }
    }

    private fun pick(cell: Cell) {
        UiManager.pop()
        onPick(cell.insert)
    }

    private companion object {
        const val CUSTOM = "custom"
        const val COLUMNS = 9
        const val CELL = 22f
        const val INSET = 3f
        const val FIELD_H = 20f
        const val HEIGHT = 250f
        const val GAP = 6f
        const val MARGIN = 8f
        const val MAX_RESULTS = 180
        const val SCROLL_GUTTER = Scroll.LANE_WIDTH + 2f
    }
}
