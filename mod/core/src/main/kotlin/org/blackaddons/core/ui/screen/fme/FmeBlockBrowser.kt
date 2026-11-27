package org.blackaddons.core.ui.screen.fme

import org.blackaddons.api.Platform
import org.blackaddons.api.input.Keys
import org.blackaddons.api.ui.anim.Animation
import org.blackaddons.api.ui.anim.AnimationMap
import org.blackaddons.api.ui.icon.LucideIcons
import org.blackaddons.api.ui.layout.Rect
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.Tooltip
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.Chevron
import org.blackaddons.api.ui.widget.Scroll
import org.blackaddons.api.ui.widget.WidgetHost
import org.blackaddons.api.ui.widget.text.TextInput
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.color.lerpColor
import org.blackaddons.api.util.containsIgnoreCase
import org.blackaddons.api.util.splitWhitespace
import org.blackaddons.api.world.BlockCatalogEntry
import org.blackaddons.core.features.impl.visual.fme.*
import java.awt.Color

internal class FmeBlockBrowser(
    private val widgets: WidgetHost,
    private val search: TextInput,
    private val scope: () -> FmeScope,
    private val contextKey: (FmeScope) -> String?,
    private val selectBlock: (String) -> Unit,
    private val openBlockMenu: (BlockCatalogEntry, Rect) -> Unit,
    private val openChangedMenu: (FmeManagedGroup, Rect) -> Unit,
) {
    private val scroll = widgets.add(Scroll())
    private val bounds = Rect()
    private val collapsedChanged = mutableSetOf<FmeScope>()
    private val changedAnimations = FmeScope.entries.associateWith { Animation(1f, Theme.ANIM_NORMAL.toLong()) }
    private val changedHoverAnimations = AnimationMap<String>()
    private val changedSelectionAnimations = AnimationMap<String>()
    private val blockHoverAnimations = AnimationMap<String>()
    private val blockSelectionAnimations = AnimationMap<String>()
    private val allBlocks = Platform.world.registeredBlocks
    private val blocksById = allBlocks.associateBy(BlockCatalogEntry::blockId)
    private var cachedQuery: String? = null
    private var cachedFavorites = emptySet<String>()
    private var cachedBlocks = emptyList<BlockCatalogEntry>()
    private var selectedChangedKey: String? = null

    fun entry(blockId: String): BlockCatalogEntry? = blocksById[blockId]

    fun render(x: Float, y: Float, w: Float, h: Float) {
        bounds.set(x, y, w, h)
        Render.rect(x, y, w, h, Theme.SURFACE.withAlpha(90), Theme.RADIUS_SMALL)

        val groups = matchingGroups()
        val availableW = w - BROWSER_INSET * 2f - Scroll.LANE_WIDTH
        val columns = columnCount(availableW)
        val blockRows = matchingBlocks().chunked(columns)
        val changedRows = changedRows(groups, availableW)
        val activeScope = scope()
        val changedProgress = changedAnimations.getValue(activeScope).also {
            it.animateTo(if (activeScope in collapsedChanged && search.text.isBlank()) 0f else 1f)
        }.get()
        val changedGridH = if (changedRows.isEmpty()) 0f else changedRows.size * CHANGED_STEP_Y + CHANGED_GAP
        val changedHeight = if (groups.isEmpty()) 0f else SECTION_H + (changedGridH + Theme.PADDING_SMALL) * changedProgress
        val contentHeight = BROWSER_INSET + changedHeight + SECTION_H + blockRows.size * CELL_STEP + BROWSER_INSET
        scroll.render(x, y, w, h, contentHeight) { offset ->
            var cy = y + BROWSER_INSET - offset
            if (groups.isNotEmpty()) {
                drawChangedHeader(groups, x + BROWSER_INSET, cy, w - BROWSER_INSET * 2f - Scroll.LANE_WIDTH)
                cy += SECTION_H
                if (changedProgress > 0f) {
                    Render.pushScissor(x, cy, w, changedGridH * changedProgress)
                    drawChangedGrid(changedRows, x + BROWSER_INSET, cy + CHANGED_GAP, availableW)
                    Render.popScissor()
                    cy += (changedGridH + Theme.PADDING_SMALL) * changedProgress
                }
            }

            drawSectionHeader(tr("all_blocks", "All blocks"), x + BROWSER_INSET, cy, availableW)
            cy += SECTION_H
            blockRows.forEach { row ->
                drawBlockRow(row, x + BROWSER_INSET, cy, availableW, columns)
                cy += CELL_STEP
            }
            if (blockRows.isEmpty()) {
                val empty = tr("empty", "No blocks match.")
                Render.text(empty, x + (w - Render.textWidth(empty, TextStyle.SMALL_MUTED)) / 2f, cy + 12f, TextStyle.SMALL_MUTED)
            }
        }
    }

    fun contains(mouseX: Float, mouseY: Float): Boolean = bounds.has(mouseX, mouseY)

    fun reset() {
        scroll.reset()
        selectedChangedKey = null
    }

    fun invalidateBlocks() {
        cachedQuery = null
        cachedFavorites = emptySet()
    }

    private fun drawChangedHeader(groups: List<FmeManagedGroup>, x: Float, y: Float, w: Float) {
        if (!visibleY(y, SECTION_H)) return
        widgets.clickable(x, y, w, SECTION_H) {
            if (search.text.isBlank()) {
                val scope = scope()
                if (!collapsedChanged.remove(scope)) collapsedChanged += scope
            }
        }
        val activeScope = scope()
        val expanded = activeScope !in collapsedChanged || search.text.isNotBlank()
        Chevron.draw(x + 6f, y + SECTION_H / 2f, 4f, 1.5f, if (expanded) 1f else 0f, Theme.TEXT_SECONDARY)
        val count = groups.sumOf(FmeManagedGroup::count)
        drawSectionHeader(tr("changed_blocks", "Changed blocks (%d)", count), x + 16f, y, w - 16f)
    }

    private fun drawSectionHeader(label: String, x: Float, y: Float, w: Float) {
        if (!visibleY(y, SECTION_H)) return
        Render.text(label, x, Render.centerY(y, SECTION_H, TextStyle.SMALL_SECONDARY_MEDIUM), TextStyle.SMALL_SECONDARY_MEDIUM)
        val labelW = Render.textWidth(label, Theme.FONT_SMALL, Fonts.medium)
        val lineX = x + labelW + 8f
        if (lineX < x + w) Render.rect(lineX, y + SECTION_H / 2f, x + w - lineX, 1f, Theme.BORDER)
    }

    private fun drawChangedGrid(rows: List<List<FmeManagedGroup>>, x: Float, y: Float, w: Float) {
        rows.forEachIndexed { rowIndex, row ->
            val rowY = y + rowIndex * CHANGED_STEP_Y
            if (!visibleY(rowY, CHANGED_CARD_H)) return@forEachIndexed
            var cardX = x + (w - changedGridWidth(row)) / 2f
            row.forEach { group ->
                drawChangedCard(group, cardX, rowY)
                cardX += changedCardWidth(group) + CHANGED_GAP
            }
        }
    }

    private fun drawChangedCard(group: FmeManagedGroup, x: Float, y: Float) {
        val sameBlock = group.sourceBlockId == group.blockId
        val cardW = changedCardWidth(group)
        val key = animationKey(group)
        val hovered = widgets.clickable(x, y, cardW, CHANGED_CARD_H) {
            selectedChangedKey = key
            if (group.blockId != FME_ANY_SOURCE) selectBlock(group.blockId)
        }
        widgets.clickable(x, y, cardW, CHANGED_CARD_H, Keys.MOUSE_RIGHT) {
            openChangedMenu(group, Rect(x, y, cardW, CHANGED_CARD_H))
        }
        val hover = changedHoverAnimations[key, hovered]
        val selection = changedSelectionAnimations[key, key == selectedChangedKey]
        val base = if (group.enabled) Theme.SURFACE_ACTIVE.withAlpha(180) else Theme.SURFACE.withAlpha(65)
        val background = lerpColor(lerpColor(base, Theme.ACCENT.withAlpha(78), selection), Theme.SURFACE_HOVER, hover)
        Render.rect(x, y, cardW, CHANGED_CARD_H, background, Theme.RADIUS_SMALL)
        if (selection > 0f) {
            Render.hollowRect(
                x, y, cardW, CHANGED_CARD_H, 1f,
                Theme.ACCENT.withAlpha((selection * 255f).toInt()), Theme.RADIUS_SMALL,
            )
        }
        val itemSize = CHANGED_ITEM_SIZE + hover
        val itemOffset = (CHANGED_ITEM_SIZE - itemSize) / 2f
        val sourceX = if (sameBlock) x + (cardW - CHANGED_ITEM_SIZE) / 2f else x + 5f
        if (group.sourceBlockId == FME_ANY_SOURCE) {
            val markX = sourceX + (CHANGED_ITEM_SIZE - Render.textWidth(ANY_MARK, Theme.FONT_BODY, Fonts.medium)) / 2f
            Render.text(ANY_MARK, markX, y + 11f, TextStyle.BODY_MUTED_MEDIUM)
        } else {
            blocksById[group.sourceBlockId]?.let { Render.item(it.itemId, sourceX + itemOffset, y + 7f + itemOffset, itemSize, itemSize, Theme.RADIUS_SMALL) }
        }
        if (!sameBlock) {
            Render.text("→", x + 31f, y + 12f, TextStyle.SMALL_MUTED_MEDIUM)
            blocksById[group.blockId]?.let { Render.item(it.itemId, x + 44f + itemOffset, y + 7f + itemOffset, itemSize, itemSize, Theme.RADIUS_SMALL) }
        }
        if (group.kind == FmeManagedKind.POSITION && group.count > 1) {
            Render.text("×${group.count}", x + 4f, y + CHANGED_CARD_H - 10f, META_FONT, Theme.TEXT_MUTED, Fonts.medium)
        }
        val tint = Color(group.tintRgb)
        Render.rect(x + cardW - 9f, y + CHANGED_CARD_H - 9f, 5f, 5f, tint, 2.5f)
        Render.hollowRect(x + cardW - 9f, y + CHANGED_CARD_H - 9f, 5f, 5f, 1f, Theme.BORDER, 2.5f)
        if (!group.enabled) Render.icon(LucideIcons.eyeOff, x + cardW - 13f, y + 3f, 9f, Theme.TEXT_DISABLED)
        if (hovered) Tooltip.show(groupTooltip(group))
    }

    // this is horrible
    private fun groupTooltip(group: FmeManagedGroup): String = buildString {
        append(displayName(group.sourceBlockId))
        if (group.sourceBlockId != group.blockId) append(" → ").append(displayName(group.blockId))
        append('\n').append(group.sourceBlockId)
        if (group.sourceBlockId != group.blockId) append(" → ").append(group.blockId)
        append('\n').append(if (group.kind == FmeManagedKind.TYPE) tr("type_rule", "Type rule") else tr("position_edit", "Position edit"))
        if (group.kind == FmeManagedKind.POSITION) append(" ×").append(group.count)
        append('\n').append(tr("scope_value", "Scope: %s", group.scopeKey.ifEmpty { tr("everywhere", "Everywhere") }))
        append('\n').append(tr("tint_value", "Tint: #%06X", group.tintRgb))
        Fme.groupStates(group).filter(Map<String, String>::isNotEmpty).forEach { state ->
            append('\n').append(tr("state_value", "State: %s", state.entries.joinToString(", ") { "${it.key}=${it.value}" }))
        }
        if (!group.enabled) append('\n').append(tr("disabled", "Disabled"))
        if (group.ghost) append('\n').append(tr("visual_block", "Visual block"))
    }

    private fun drawBlockRow(blocks: List<BlockCatalogEntry>, x: Float, y: Float, w: Float, columns: Int) {
        if (!visibleY(y, CELL_SIZE)) return
        val startX = x + (w - gridWidth(columns)) / 2f
        blocks.forEachIndexed { index, entry ->
            val cellX = startX + index * CELL_STEP
            val selected = entry.blockId == Fme.selectedBlockId()
            val favorite = entry.blockId in cachedFavorites
            val any = entry === ANY_ENTRY
            val hovered = widgets.clickable(cellX, y, CELL_SIZE, CELL_SIZE) {
                selectedChangedKey = null
                if (any) openBlockMenu(entry, Rect(cellX, y, CELL_SIZE, CELL_SIZE)) else selectBlock(entry.blockId)
            }
            widgets.clickable(cellX, y, CELL_SIZE, CELL_SIZE, Keys.MOUSE_RIGHT) {
                openBlockMenu(entry, Rect(cellX, y, CELL_SIZE, CELL_SIZE))
            }
            val hover = blockHoverAnimations[entry.blockId, hovered]
            val selection = blockSelectionAnimations[entry.blockId, selected]
            val base = lerpColor(Theme.SURFACE.withAlpha(55), Theme.ACCENT.withAlpha(105), selection)
            val target = lerpColor(Theme.SURFACE_HOVER.withAlpha(150), Theme.ACCENT.withAlpha(150), selection)
            val background = lerpColor(base, target, hover)
            Render.rect(cellX, y, CELL_SIZE, CELL_SIZE, background, Theme.RADIUS_SMALL)
            if (selection > 0f) {
                Render.hollowRect(cellX, y, CELL_SIZE, CELL_SIZE, 1f, Theme.ACCENT.withAlpha((selection * 255f).toInt()), Theme.RADIUS_SMALL)
            }
            val itemSize = ITEM_SIZE + hover * 1.5f
            val itemInset = (CELL_SIZE - itemSize) / 2f
            if (any) {
                val markX = cellX + (CELL_SIZE - Render.textWidth(ANY_MARK, Theme.FONT_BODY, Fonts.medium)) / 2f
                Render.text(ANY_MARK, markX, Render.centerY(y, CELL_SIZE, TextStyle.BODY_MUTED_MEDIUM), TextStyle.BODY_MUTED_MEDIUM)
            } else {
                Render.item(entry.itemId, cellX + itemInset, y + itemInset, itemSize, itemSize, Theme.RADIUS_SMALL)
            }
            if (favorite) Render.icon(LucideIcons.starFilled, cellX + CELL_SIZE - 9f, y + 2f, 7f, Theme.ACCENT)
            if (hovered) Tooltip.show("${displayName(entry.blockId)}\n${if (any) tr("change_tint", "Change tint...") else entry.blockId}")
        }
    }

    private fun matchingGroups(): List<FmeManagedGroup> {
        val activeScope = scope()
        val key = contextKey(activeScope) ?: return emptyList()
        val terms = search.text.lowercase().splitWhitespace()
        return Fme.managedGroups(activeScope, key).filter { group ->
            terms.isEmpty() || terms.all { term ->
                group.sourceBlockId.containsIgnoreCase(term) || group.blockId.containsIgnoreCase(term) ||
                    displayName(group.sourceBlockId).containsIgnoreCase(term) || displayName(group.blockId).containsIgnoreCase(term)
            }
        }
    }

    private fun matchingBlocks(): List<BlockCatalogEntry> {
        val favorites = Fme.favoriteBlockIds().toHashSet()
        if (search.text == cachedQuery && favorites == cachedFavorites) return cachedBlocks
        cachedQuery = search.text
        cachedFavorites = favorites
        val terms = search.text.lowercase().splitWhitespace()
        val (favoriteEntries, otherEntries) = allBlocks.partition { it.blockId in favorites }
        val ordered = favoriteEntries + otherEntries
        cachedBlocks = if (terms.isEmpty()) listOf(ANY_ENTRY) + ordered else ordered.filter { entry ->
            val simple = entry.blockId.substringAfter(':')
            terms.all { term -> entry.blockId.containsIgnoreCase(term) || entry.itemId.containsIgnoreCase(term) || simple.containsIgnoreCase(term) }
        }
        return cachedBlocks
    }

    private fun changedRows(groups: List<FmeManagedGroup>, width: Float): List<List<FmeManagedGroup>> {
        val rows = mutableListOf<MutableList<FmeManagedGroup>>()
        var rowWidth = 0f
        groups.forEach { group ->
            val cardW = changedCardWidth(group)
            if (rows.isEmpty() || rowWidth + CHANGED_GAP + cardW > width) {
                rows += mutableListOf(group)
                rowWidth = cardW
            } else {
                rows.last() += group
                rowWidth += CHANGED_GAP + cardW
            }
        }
        return rows
    }

    private fun changedCardWidth(group: FmeManagedGroup): Float =
        if (group.sourceBlockId == group.blockId) CHANGED_SAME_CARD_W else CHANGED_CARD_W

    private fun changedGridWidth(groups: List<FmeManagedGroup>): Float =
        groups.sumOf { changedCardWidth(it).toDouble() }.toFloat() + (groups.size - 1).coerceAtLeast(0) * CHANGED_GAP

    private fun visibleY(y: Float, h: Float): Boolean = y + h >= bounds.y && y <= bounds.y + bounds.h

    private fun animationKey(group: FmeManagedGroup): String = buildString {
        append(group.kind).append('|').append(group.scope).append('|').append(group.scopeKey)
        append('|').append(group.sourceBlockId).append('|').append(group.blockId).append('|').append(group.ghost)
        append('|').append(group.tintRgb).append('|').append(group.enabled)
    }


    internal companion object {
        val tr = I18n.scope("blackaddons.fme")

        const val BROWSER_INSET = 8f
        const val SECTION_H = 22f
        const val CHANGED_CARD_W = 76f
        const val CHANGED_SAME_CARD_W = 42f
        const val CHANGED_CARD_H = 36f
        const val CHANGED_GAP = 4f
        const val CHANGED_STEP_Y = CHANGED_CARD_H + CHANGED_GAP
        const val CELL_SIZE = 32f
        const val CELL_GAP = 4f
        const val CELL_STEP = CELL_SIZE + CELL_GAP
        const val ITEM_SIZE = 26f
        const val CHANGED_ITEM_SIZE = 22f
        const val META_FONT = 8f
        const val ANY_MARK = "*"
        val ANY_ENTRY = BlockCatalogEntry(FME_ANY_SOURCE, "")

        fun columnCount(width: Float): Int = ((width + CELL_GAP) / CELL_STEP).toInt().coerceAtLeast(1)
        fun gridWidth(columns: Int): Float = columns * CELL_SIZE + (columns - 1) * CELL_GAP
        fun displayName(id: String): String =
            if (id == FME_ANY_SOURCE) tr("any_block", "Any block") else id.substringAfter(':').replace('_', ' ').replaceFirstChar(Char::uppercase)
    }
}
