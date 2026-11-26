package org.blackaddons.api.ui.layout

import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.Tooltip
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Glass
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.ProgressBar
import org.blackaddons.api.ui.widget.Scroll
import java.awt.Color

/**
 * Pages of data built from cards: one header style, rows centered on one line height, and every height measured
 * from the content instead of typed in. The Profile Viewer is made of these, so a page built from them looks like
 * ours. The rows ([sectionHeader], [valueRow], [stats], [progressRow], [grid]) go inside a [card].
 */
object Cards {

    /** The height of one row: [valueRow], [progressRow], [note]. */
    val ROW_H: Float get() = Theme.FONT_SMALL + Theme.PADDING_SMALL + 2f

    /** The gap between cards. */
    val GAP: Float get() = Theme.PADDING

    /** The height of a [sectionHeader]. */
    val HEADER_H: Float get() = Render.textHeight(Theme.FONT_SMALL, Fonts.bold) + Theme.PADDING_SMALL

    /** The lane [scrolled] gives up for its scrollbar. */
    const val SCROLL_GUTTER = 12f

    /** Strokes reach half a pixel past their box, and this keeps them inside a scissor. */
    const val INSET = 1f

    private const val HINT_R = 4.5f
    private const val BAR_H = 4f

    /**
     * A card: an optional [title] header (with [trailing] text on the right), then [content] at the inner width. A
     * [minHeight] stretches it, so the last card can end on the page's bottom edge; [centered] then puts the
     * content in the middle instead of at the top. A [footer] sits at the bottom.
     */
    fun card(
        w: Float,
        title: String? = null,
        trailing: String? = null,
        minHeight: Float = 0f,
        centered: Boolean = false,
        spacing: Float = Theme.PADDING_SMALL,
        footer: (LayoutBuilder.(innerW: Float) -> Unit)? = null,
        content: LayoutBuilder.(innerW: Float) -> Unit,
    ): LayoutNode {
        val innerW = w - Theme.PADDING * 2f
        val body = column(spacing = spacing, width = innerW) {
            if (title != null) sectionHeader(innerW, title, trailing)
            content(innerW)
        }
        val bottom = footer?.let { build -> column(width = innerW) { build(this, innerW) } }
        val natural = body.height + (bottom?.let { it.height + spacing } ?: 0f)
        val innerH = maxOf(natural, minHeight - Theme.PADDING * 2f)
        return column(width = innerW, height = innerH) {
            if (centered) flexSpacer()
            add(body)
            if (centered || bottom != null) flexSpacer()
            bottom?.let { add(it) }
        }.padded(Theme.PADDING).background(Theme.SURFACE, Theme.RADIUS)
    }

    /** A [w] x [h] card with a centered [title] and [detail] line, for a page with nothing to show. */
    fun empty(w: Float, h: Float, title: String, detail: String): LayoutNode = Cell(w, h) { x, y ->
        drawEmpty(x, y, w, h, title, detail)
    }

    /** A card's background, for code that does not build a layout tree. */
    fun drawCard(x: Float, y: Float, w: Float, h: Float) = Render.glass(x, y, w, h, Glass.CARD)

    /** [empty], drawn in place. */
    fun drawEmpty(x: Float, y: Float, w: Float, h: Float, title: String, detail: String) {
        drawCard(x, y, w, h)
        Render.textCentered(title, x + w / 2f, y + h / 2f - Theme.FONT_BODY, TextStyle.BODY_PRIMARY_MEDIUM, maxWidth = w)
        Render.textCentered(detail, x + w / 2f, y + h / 2f + Theme.PADDING_SMALL, TextStyle.SMALL_MUTED, maxWidth = w)
    }

    /**
     * Draws the tree [build] makes inside a scrolling box. It is built at the full width first, and again without
     * the scrollbar's lane only when it turns out taller than the box, so a short page keeps its full width.
     */
    fun scrolled(scroll: Scroll, x: Float, y: Float, w: Float, h: Float, build: (width: Float) -> LayoutNode) {
        val fullW = (w - INSET * 2f).coerceAtLeast(0f)
        var node = build(fullW)
        if (node.height > h) node = build((fullW - SCROLL_GUTTER).coerceAtLeast(0f))
        scroll.render(x, y, w, h, node.height + INSET * 2f) { offset ->
            node.draw(x + INSET, y + INSET - offset)
        }
    }

    /** [sectionHeader], drawn in place, for a list's own rows. */
    fun drawHeader(
        x: Float,
        y: Float,
        w: Float,
        title: String,
        trailing: String? = null,
        trailingColor: Color = Theme.TEXT_MUTED,
        trailingTip: String? = null,
    ) {
        val label = title.uppercase()
        Render.text(label, x, y, TextStyle.SMALL_SECONDARY_BOLD, maxWidth = w)
        if (trailing != null) {
            val room = w - Render.textWidth(label, TextStyle.SMALL_SECONDARY_BOLD) - Theme.PADDING
            val textW = Render.textRight(trailing, x + w, y, Theme.FONT_SMALL, trailingColor, Fonts.medium, maxWidth = room)
            Tooltip.showOver(x + w - textW, y, textW, HEADER_H, trailingTip)
        }
        Render.rect(x, y + HEADER_H - 1f, w, 1f, Theme.BORDER)
    }

    /** The vector "i" disc that shows a row has a tooltip, so no font can miss the glyph. */
    fun drawHintBadge(cx: Float, cy: Float) {
        Render.circle(cx, cy, HINT_R, Theme.SURFACE_HOVER)
        Render.circle(cx, cy - HINT_R * 0.45f, 0.9f, Theme.TEXT_SECONDARY)
        Render.rect(cx - 0.9f, cy - HINT_R * 0.1f, 1.8f, HINT_R * 0.75f, Theme.TEXT_SECONDARY, 0.9f)
    }

    /** A thin progress bar centered on a row of height [h]. */
    fun drawBar(x: Float, y: Float, w: Float, h: Float, progress: Double, color: Color) {
        ProgressBar.draw(x, y + h / 2f - BAR_H / 2f, w.coerceAtLeast(0f), BAR_H, progress, fill = color)
    }
}

/** One labeled value of a [stats] block. [tooltip] shows on hover. */
class Stat(val label: String, val value: String, val color: Color = Theme.TEXT_PRIMARY, val tooltip: String? = null)

/**
 * A section title, set apart by case, weight and a rule. [trailing] sits on the right in [trailingColor], and
 * [trailingTip] shows when it is hovered.
 */
fun LayoutBuilder.sectionHeader(
    w: Float,
    title: String,
    trailing: String? = null,
    trailingColor: Color = Theme.TEXT_MUTED,
    trailingTip: String? = null,
) {
    cell(w, Cards.HEADER_H) { x, y -> Cards.drawHeader(x, y, w, title, trailing, trailingColor, trailingTip) }
}

/** Label on the left, value on the right. [tip] shows on hover, and [hint] adds a badge so players find it. */
fun LayoutBuilder.valueRow(
    w: Float,
    label: String,
    value: String,
    valueColor: Color = Theme.TEXT_PRIMARY,
    tip: String? = null,
    hint: Boolean = false,
) {
    val rowH = Cards.ROW_H
    cell(w, rowH) { x, y ->
        val ty = Render.centerY(y, rowH, Theme.FONT_SMALL, Fonts.regular)
        val valueW = Render.textRight(value, x + w, ty, Theme.FONT_SMALL, valueColor, Fonts.medium, maxWidth = w)
        val labelW = Render.text(label, x, ty, Theme.FONT_SMALL, Theme.TEXT_MUTED, Fonts.regular, maxWidth = (w - valueW - Theme.PADDING).coerceAtLeast(0f))
        if (hint) Cards.drawHintBadge(x + labelW + 5f, y + rowH / 2f)
        Tooltip.showOver(x, y, w, rowH, tip)
    }
}

/** A muted line, for "nothing recorded" inside a card. */
fun LayoutBuilder.note(w: Float, text: String) {
    val rowH = Cards.ROW_H
    cell(w, rowH) { x, y ->
        Render.text(text, x, Render.centerY(y, rowH, TextStyle.SMALL_MUTED), TextStyle.SMALL_MUTED, maxWidth = w)
    }
}

/** A label in small caps over its value. */
fun LayoutBuilder.stat(w: Float, stat: Stat) {
    val labelH = Render.textHeight(Theme.FONT_SMALL, Fonts.bold)
    val valueH = Render.textHeight(Theme.FONT_BODY, Fonts.medium)
    val h = labelH + Theme.PADDING_SMALL + valueH
    cell(w, h) { x, y ->
        Render.text(stat.label.uppercase(), x, y, TextStyle.SMALL_MUTED_BOLD, maxWidth = w)
        Render.text(stat.value, x, y + labelH + Theme.PADDING_SMALL, Theme.FONT_BODY, stat.color, Fonts.medium, maxWidth = w)
        Tooltip.showOver(x, y, w, h, stat.tooltip)
    }
}

/** [stats] in [columns] equal columns, wrapping into as many rows as they need. */
fun LayoutBuilder.stats(w: Float, stats: List<Stat>, columns: Int = stats.size.coerceAtLeast(1)) {
    val gap = Theme.PADDING
    val columnW = columnWidth(w, columns, gap)
    column(spacing = Theme.PADDING, width = w) {
        stats.chunked(columns).forEach { rowItems ->
            row(spacing = gap, width = w) { rowItems.forEach { stat(columnW, it) } }
        }
    }
}

/**
 * Label, progress bar and value on one row. [labelW] lines the bars up across rows, and a [dot] color draws a
 * marker before the label.
 */
fun LayoutBuilder.progressRow(
    w: Float,
    label: String,
    value: String,
    progress: Double,
    color: Color,
    labelW: Float,
    dot: Color? = null,
    tip: String? = null,
) {
    val rowH = Cards.ROW_H
    cell(w, rowH) { x, y ->
        val ty = Render.centerY(y, rowH, Theme.FONT_SMALL, Fonts.medium)
        var labelX = x
        if (dot != null) {
            Render.circle(x + DOT_R, y + rowH / 2f, DOT_R, dot)
            labelX += DOT_R * 2f + Theme.PADDING_SMALL
        }
        Render.text(label, labelX, ty, TextStyle.SMALL_PRIMARY_MEDIUM, maxWidth = labelW)
        val valueW = Render.textWidth(value, Theme.FONT_SMALL, Fonts.medium)
        Render.text(value, x + w - valueW, ty, Theme.FONT_SMALL, color, Fonts.medium)
        val barX = labelX + labelW + Theme.PADDING
        Cards.drawBar(barX, y, x + w - valueW - Theme.PADDING - barX, rowH, progress, color)
        Tooltip.showOver(x, y, w, rowH, tip)
    }
}

private const val DOT_R = 3f

/** [count] equal cells of height [cellH] in [columns] columns; [draw] paints cell [index] at its position. */
fun LayoutBuilder.grid(
    w: Float,
    count: Int,
    columns: Int,
    cellH: Float,
    gap: Float = Cards.GAP,
    draw: (index: Int, x: Float, y: Float, cellW: Float) -> Unit,
) {
    if (count <= 0) return
    val rows = (count + columns - 1) / columns
    val cellW = columnWidth(w, columns, gap)
    cell(w, rows * cellH + (rows - 1) * gap) { x, y ->
        repeat(count) { i ->
            draw(i, x + (i % columns) * (cellW + gap), y + (i / columns) * (cellH + gap), cellW)
        }
    }
}
