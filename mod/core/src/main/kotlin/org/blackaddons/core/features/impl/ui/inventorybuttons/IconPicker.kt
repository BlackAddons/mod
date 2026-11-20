package org.blackaddons.core.features.impl.ui.inventorybuttons

import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.Popover
import org.blackaddons.api.ui.screen.Tooltip
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.Scroll
import org.blackaddons.api.ui.widget.text.TextInput
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.stripFormatting
import org.blackaddons.core.skyblock.SkyblockItems
import org.blackaddons.core.skyblock.rarityColor
import kotlin.math.min

private val tr = I18n.scope("blackaddons.inventory_buttons.picker")

internal class PickerItem(val id: String, val name: String, val tier: String?) {
    val nameKey = name.lowercase()
    val idKey = id.lowercase()
}

internal fun matchItems(items: List<PickerItem>, query: String): List<PickerItem> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return items
    val words = q.split(' ').filter(String::isNotEmpty)
    return items.mapNotNull { item ->
        val rank = when {
            item.nameKey == q || item.idKey == q -> 0
            item.nameKey.startsWith(q) || item.idKey.startsWith(q) -> 1
            words.all { item.nameKey.contains(it) || item.idKey.contains(it) } -> 2
            else -> return@mapNotNull null
        }
        rank to item
    }.sortedBy { it.first }.map { it.second }
}

// Beside the editor card (sideLeft..sideRight, panel units), so the two never overlap.
internal class IconPicker(
    private val sideLeft: Float,
    private val sideRight: Float,
    private val top: Float,
    private val current: String,
    private val onPick: (String) -> Unit,
) : Popover() {

    private val scroll = widgets.add(Scroll())

    private var query = ""
    private var results: List<PickerItem> = emptyList()
    private var resultsFor: String? = null

    private val search = widgets.add(TextInput(placeholder = tr("search_hint", "Search by name or id"))).apply {
        onTextChanged = {
            query = it
            scroll.scrollTo(0f)
        }
        onEnter = { results.firstOrNull()?.let(::pick) }
    }

    override fun onShow() {
        widgets.focus(search)
    }

    override fun render(mouseX: Float, mouseY: Float, deltaTicks: Float) {
        val items = items()
        if (items == null) ButtonIcons.requestItemTable()
        val shown = items?.let(::filtered).orEmpty()
        val h = Theme.PADDING * 2f + FIELD_H + GAP + GRID_H
        place(h)

        drawCard(rise = RISE) {
            val x = card.x + Theme.PADDING
            var y = card.y + Theme.PADDING
            search.render(x, y, INNER_W, FIELD_H, mouseX, mouseY)
            y += FIELD_H + GAP
            when {
                items == null && ButtonIcons.itemTableFailed -> message(tr("failed", "Couldn't load the item list. Trying again..."), x, y)
                items == null -> message(tr("loading", "Loading items..."), x, y)
                shown.isEmpty() -> message(tr("no_match", "No items match"), x, y)
                else -> grid(shown, x, y)
            }
        }
    }

    private fun place(h: Float) {
        val right = sideRight + ANCHOR_GAP
        val x = if (right + WIDTH + MARGIN <= UiManager.screenWidth) right else (sideLeft - ANCHOR_GAP - WIDTH).coerceAtLeast(MARGIN)
        val y = top.coerceIn(MARGIN, maxOf(MARGIN, UiManager.screenHeight - h - MARGIN))
        card.set(x, y, WIDTH, h)
    }

    private fun grid(shown: List<PickerItem>, x: Float, y: Float) {
        val rows = (shown.size + COLS - 1) / COLS
        scroll.render(x, y, INNER_W, GRID_H, rows * CELL) { offset ->
            val first = (offset / CELL).toInt().coerceAtLeast(0)
            val last = min(rows - 1, ((offset + GRID_H) / CELL).toInt())
            for (row in first..last) {
                for (col in 0 until COLS) {
                    val item = shown.getOrNull(row * COLS + col) ?: break
                    cell(item, x + col * CELL, y + row * CELL - offset)
                }
            }
        }
    }

    private fun cell(item: PickerItem, cx: Float, cy: Float) {
        val bx = cx + CELL_INSET
        val by = cy + CELL_INSET
        val size = CELL - CELL_INSET * 2f
        val hovered = widgets.clickable(bx, by, size, size) { pick(item) }
        val rarity = item.tier?.takeIf { InventoryButtons.pickerRarity.value }?.let(::rarityColor)
        Render.rect(bx, by, size, size, if (hovered) Theme.SURFACE_HOVER else Theme.SURFACE, Theme.RADIUS_SMALL)
        rarity?.let { Render.rect(bx, by, size, size, it.withAlpha(if (hovered) HOVER_TINT else TINT), Theme.RADIUS_SMALL) }
        val border = when {
            item.id == current.trim() -> Theme.ACCENT
            hovered -> rarity ?: Theme.TEXT_MUTED
            else -> null
        }
        border?.let { Render.hollowRect(bx, by, size, size, 1f, it, Theme.RADIUS_SMALL) }
        SkyblockItems.spec(item.id)?.let {
            val inset = (size - ITEM) / 2f
            Render.itemSpec(it, bx + inset, by + inset, ITEM, ITEM)
        }
        if (hovered) Tooltip.show("${item.name}\n§8${item.id}")
    }

    private fun message(text: String, x: Float, y: Float) {
        val style = TextStyle.SMALL_MUTED
        val w = Render.textWidth(text, style.size, style.font)
        Render.text(text, x + (INNER_W - w) / 2f, y + (GRID_H - Render.textHeight(style.size, style.font)) / 2f, style)
    }

    private fun filtered(items: List<PickerItem>): List<PickerItem> {
        if (resultsFor != query) {
            results = matchItems(items, query)
            resultsFor = query
        }
        return results
    }

    private fun pick(item: PickerItem) {
        onPick(item.id)
        UiManager.pop()
    }

    private companion object {
        private var all: List<PickerItem>? = null

        fun items(): List<PickerItem>? {
            all?.let { return it }
            val ids = SkyblockItems.ids() ?: return null
            return ids.map { PickerItem(it, SkyblockItems.name(it)?.stripFormatting() ?: it, SkyblockItems.tier(it)) }
                .sortedBy { it.nameKey }
                .also { all = it }
        }

        const val COLS = 9
        const val ROWS = 6
        const val CELL = 22f
        const val CELL_INSET = 1f
        const val ITEM = 16f
        const val SCROLL_ROOM = 6f
        const val INNER_W = COLS * CELL + SCROLL_ROOM
        const val WIDTH = INNER_W + Theme.PADDING * 2f
        const val GRID_H = ROWS * CELL
        const val FIELD_H = 20f
        const val GAP = 6f
        const val ANCHOR_GAP = 4f
        const val RISE = 4f
        const val TINT = 0.25f
        const val HOVER_TINT = 0.4f
    }
}
