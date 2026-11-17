package org.blackaddons.core.ui.screen.pv.parts
import org.blackaddons.api.chat.ChatMessage
import org.blackaddons.api.chat.ChatSpan
import org.blackaddons.api.skyblock.profile.ProfileItem
import org.blackaddons.api.ui.layout.Cell
import org.blackaddons.api.ui.layout.LayoutNode
import org.blackaddons.api.ui.layout.hovered
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.Tooltip
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import java.awt.Color
import kotlin.math.ceil

/** Shared, so inventory pages and previews can't drift apart in size or hover behavior. */
internal object PvInventoryGrid {

    fun cell(width: Float, columns: Int): Float = ((width - Theme.PADDING * 2f) / columns).coerceIn(MIN_CELL, MAX_CELL)

    fun rows(size: Int, columns: Int): Int = ceil(size / columns.toDouble()).toInt().coerceAtLeast(1)

    /** The drawn width of [columns] cells. The last column has no gap after it, so centering uses this. */
    fun width(columns: Int, cell: Float): Float = columns * cell - GAP

    /** The drawn height of [size] slots in [columns] columns. */
    fun height(size: Int, columns: Int, cell: Float): Float = rows(size, columns) * cell - GAP

    /**
     * The grid as a layout node, sized to what it draws. While a search runs, [match] outlines the items it finds and
     * dims the rest.
     */
    fun node(
        columns: Int, cell: Float, slots: List<ProfileItem?>, minimumSlots: Int = slots.size,
        match: ((ProfileItem) -> Boolean)? = null,
    ): LayoutNode = Cell(width(columns, cell), height(maxOf(slots.size, minimumSlots), columns, cell)) { x, y ->
        draw(x, y, columns, cell, slots, minimumSlots, match)
    }

    fun draw(
        x: Float, y: Float, columns: Int, cell: Float, slots: List<ProfileItem?>, minimumSlots: Int = slots.size,
        match: ((ProfileItem) -> Boolean)? = null,
    ) {
        val size = cell - GAP
        val display = slots + List((minimumSlots - slots.size).coerceAtLeast(0)) { null }
        display.forEachIndexed { index, item ->
            val slotX = x + index % columns * cell
            val slotY = y + index / columns * cell
            Render.rect(slotX, slotY, size, size, Theme.BACKGROUND, Theme.RADIUS_SMALL)
            item ?: return@forEachIndexed
            Render.itemSpec(item.raw, slotX + INSET, slotY + INSET, size - INSET * 2f, size - INSET * 2f)
            drawCount(item, slotX, slotY, size)
            // A veil instead of global alpha, which is a single setting the tab's own fade-in already uses.
            when (match?.invoke(item)) {
                true -> Render.hollowRect(slotX, slotY, size, size, 1f, Theme.ACCENT, Theme.RADIUS_SMALL)
                false -> Render.rect(slotX, slotY, size, size, Theme.BACKGROUND.withAlpha(VEIL_ALPHA), Theme.RADIUS_SMALL)
                null -> {}
            }
            if (hovered(slotX, slotY, size, size)) showTooltip(item)
        }
    }

    // Placed like the game does it (white with a shadow, bottom right, only for more than one), in the UI's own face.
    private fun drawCount(item: ProfileItem, x: Float, y: Float, size: Float) {
        val count = item.info?.count ?: return
        if (count <= 1) return
        val text = count.toString()
        val fontSize = size * COUNT_SHARE
        val textW = Render.textWidth(text, fontSize, Fonts.bold)
        val textH = Render.textHeight(fontSize, Fonts.bold)
        Render.textShadow(text, x + size - INSET - textW, y + size - INSET - textH, fontSize, Color.WHITE, Fonts.bold)
    }

    fun showTooltip(item: ProfileItem) {
        val info = item.info ?: return
        val lines = listOf(info.formattedName) + info.formattedLoreLines
        val lore = ChatMessage(lines.flatMapIndexed { index, line ->
            if (index == lines.lastIndex) line.spans else line.spans + ChatSpan("\n")
        })
        Tooltip.show(lore, Tooltip.Style.ITEM)
    }

    const val GAP = 2f
    const val INSET = 2f
    const val MIN_CELL = 18f
    const val MAX_CELL = 40f

    private const val VEIL_ALPHA = 190

    // The game's count is 8 px of a 16 px slot.
    private const val COUNT_SHARE = 0.42f
}
