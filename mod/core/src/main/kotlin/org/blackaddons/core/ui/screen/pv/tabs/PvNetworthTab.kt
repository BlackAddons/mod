package org.blackaddons.core.ui.screen.pv.tabs

import org.blackaddons.api.skyblock.profile.Networth
import org.blackaddons.api.ui.anim.Animation
import org.blackaddons.api.ui.layout.*
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.Tooltip
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.DonutChart
import org.blackaddons.api.ui.widget.ScrollableList
import org.blackaddons.api.ui.widget.UiMouse
import org.blackaddons.api.ui.widget.WidgetHost
import org.blackaddons.api.util.I18n
import org.blackaddons.core.ui.screen.pv.PvFormat
import java.awt.Color
import java.util.Locale
import kotlin.math.min

private val tr = I18n.scope("blackaddons.pv")

internal class PvNetworthTab {

    internal data class Category(val key: String, val label: String, val value: Double, val color: Color)

    val widgets = WidgetHost()
    private val categoryList = widgets.add(ScrollableList<Category>().apply {
        rowHeight = CATEGORY_ROW_H
        rowGap = Theme.PADDING_SMALL
        drawBackground = false
        emptyMessage = tr("networth_detail.empty_categories", "No valued categories were reported.")
        drawRow = ::drawCategory
    })
    private val chart = widgets.add(DonutChart())
    private val chartReveal = Animation(durationMs = Theme.ANIM_NORMAL.toLong())

    fun reset() {
        categoryList.items = emptyList()
        categoryList.resetScroll()
        categoryList.clear()
        chart.segments = emptyList()
        chart.highlightedIndex = null
        chartReveal.set(0f)
    }

    fun hide() = reset()

    fun render(x: Float, y: Float, width: Float, height: Float, networth: Networth?) {
        if (networth == null) {
            reset()
            renderUnavailable(x, y, width, height)
            return
        }

        chartReveal.animateTo(1f)
        val categories = valuedCategories(networth)
        val categoryTotal = categories.sumOf(Category::value)
        chart.segments = categories.map { category ->
            DonutChart.Segment(
                category.label,
                category.value,
                category.color,
                PvFormat.networthCategoryTooltip(category.label, category.value, categoryTotal),
            )
        }
        chart.progress = chartReveal.get()
        categoryList.items = categories

        val partial = networth.unavailable.isNotEmpty()
        val top = column(width = width) {
            sectionHeader(
                width, tr("networth", "Networth"),
                trailing = if (partial) tr("networth_detail.partial", "Partial estimate") else null,
                trailingColor = Theme.DANGER,
                trailingTip = if (partial) hiddenTooltip(networth.unavailable) else null,
            )
        }
        val metricW = (width - Cards.GAP * 2f) / 3f
        val metrics = row(spacing = Cards.GAP, width = width) {
            add(metricCard(metricW, tr("networth", "Networth"), networth.total, available = true, partial = partial))
            add(metricCard(metricW, tr("purse_label", "Purse"), networth.purse, available = "coins" !in networth.unavailable, partial = false))
            add(metricCard(metricW, tr("bank", "Bank"), networth.bank, available = "bank" !in networth.unavailable, partial = false))
        }
        val breakdownH = (height - top.height - metrics.height - Cards.GAP * 2f).coerceAtLeast(0f)
        column(spacing = Cards.GAP, width = width) {
            add(top)
            add(metrics)
            add(breakdownCard(width, breakdownH, categoryTotal))
        }.draw(x, y)
    }

    private fun renderUnavailable(x: Float, y: Float, width: Float, height: Float) {
        val title = tr("networth_detail.unavailable_title", "Networth unavailable")
        val detail = tr("networth_detail.unavailable_detail", "The backend did not return a valuation for this profile.")
        Cards.empty(width, height, title, detail).draw(x, y)
    }

    private fun metricCard(width: Float, label: String, value: Double, available: Boolean, partial: Boolean): LayoutNode {
        val apiOff = tr("networth_detail.api_off", "API off")
        val color = when {
            !available -> Theme.DANGER
            partial -> Theme.TEXT_SECONDARY
            else -> Theme.TEXT_PRIMARY
        }
        val tooltip = if (available) "$label: ${PvFormat.exact(value)}" else "$label: $apiOff"
        return Cards.card(width) { innerW ->
            stat(innerW, Stat(label, if (available) PvFormat.compact(value) else apiOff, color, tooltip))
        }
    }

    private fun breakdownCard(width: Float, height: Float, categoryTotal: Double): LayoutNode =
        Cards.card(width, minHeight = height) { innerW ->
            val innerH = (height - Theme.PADDING * 2f).coerceAtLeast(0f)
            val chartPaneW = min(CHART_PANE_MAX_W, innerW * CHART_PANE_PORTION)
            val listW = (innerW - chartPaneW - Theme.PADDING).coerceAtLeast(0f)
            row(spacing = Theme.PADDING, width = innerW) {
                cell(chartPaneW, innerH) { cx, cy -> drawChart(cx, cy, chartPaneW, innerH, categoryTotal) }
                column(spacing = Theme.PADDING_SMALL, width = listW) {
                    sectionHeader(listW, tr("networth_detail.categories", "Valued Categories"))
                    val listH = (innerH - Render.textHeight(Theme.FONT_SMALL, Fonts.bold) - Theme.PADDING_SMALL * 2f).coerceAtLeast(0f)
                    cell(listW, listH) { lx, ly -> categoryList.render(lx, ly, listW, listH) }
                }
            }
        }

    private fun drawChart(x: Float, y: Float, width: Float, height: Float, categoryTotal: Double) {
        val diameter = minOf(height, width, CHART_DIAMETER).coerceAtLeast(0f)
        val chartX = centered(x, width, diameter)
        val chartY = centered(y, height, diameter)
        chart.boxWidth = diameter
        chart.boxHeight = diameter
        chart.highlightedIndex = categoryList.rowAt(UiMouse.x, UiMouse.y)
            .takeIf { it in categoryList.items.indices }
        chart.draw(chartX, chartY)

        val centerX = chartX + diameter / 2f
        val centerY = chartY + diameter / 2f
        val active = chart.activeIndex?.let(categoryList.items::getOrNull)
        if (active == null) {
            Render.textCentered(tr("networth_detail.assets", "Assets"), centerX, centerY - Theme.FONT_SMALL, TextStyle.SMALL_SECONDARY)
            Render.textCentered(PvFormat.compact(categoryTotal), centerX, centerY + Theme.PADDING_SMALL, TextStyle.SMALL_PRIMARY_MEDIUM)
        } else {
            val share = if (categoryTotal > 0.0) active.value / categoryTotal * 100.0 else 0.0
            Render.textCentered(
                active.label, centerX, centerY - Theme.FONT_SMALL - Theme.PADDING_SMALL, TextStyle.SMALL_PRIMARY_MEDIUM,
                maxWidth = diameter * CENTER_TEXT_PORTION,
            )
            Render.textCentered(PvFormat.compact(active.value), centerX, centerY + 1f, TextStyle.SMALL_PRIMARY_MEDIUM)
            Render.textCentered("%.1f%%".format(Locale.ROOT, share), centerX, centerY + Theme.FONT_SMALL + Theme.PADDING_SMALL, Theme.FONT_SMALL, active.color, Fonts.medium)
        }
    }

    private fun drawCategory(category: Category, index: Int, x: Float, y: Float, width: Float) {
        val hovered = hovered(x, y, width, CATEGORY_ROW_H)
        val active = hovered || index == chart.activeIndex
        if (active) Render.rect(x, y, width, CATEGORY_ROW_H, Theme.SURFACE_HOVER, Theme.RADIUS_SMALL)
        val dotX = x + Theme.PADDING_SMALL + CATEGORY_DOT
        Render.circle(dotX, y + CATEGORY_ROW_H / 2f, CATEGORY_DOT + if (active) 1f else 0f, category.color)
        val value = PvFormat.compact(category.value)
        val valueW = Render.textWidth(value, Theme.FONT_SMALL, Fonts.medium)
        val labelX = dotX + CATEGORY_DOT + Theme.PADDING_SMALL
        val labelW = (width - (labelX - x) - valueW - Theme.PADDING_SMALL).coerceAtLeast(0f)
        Render.text(
            category.label, labelX, Render.centerY(y, CATEGORY_ROW_H, Theme.FONT_SMALL, Fonts.regular), Theme.FONT_SMALL,
            if (active) Theme.TEXT_PRIMARY else Theme.TEXT_MUTED, Fonts.regular, maxWidth = labelW,
        )
        Render.text(value, x + width - valueW, Render.centerY(y, CATEGORY_ROW_H, TextStyle.SMALL_PRIMARY_MEDIUM), TextStyle.SMALL_PRIMARY_MEDIUM)
        if (hovered) Tooltip.show(chart.segments[index].tooltip)
    }

    private fun hiddenTooltip(unavailable: Set<String>): String = I18n.trOr(
        "blackaddons.pv.networth_detail.partial_tooltip", "API-hidden: %s",
    ).format(unavailable.sorted().joinToString(", ") { PvFormat.networthCategory(it) })

    companion object {
        /** Only positive finite categories take chart area. Ties sort by key. */
        internal fun valuedCategories(networth: Networth): List<Category> {
            val valued = networth.categories.entries
                .filter { (_, value) -> value.isFinite() && value > 0.0 }
                .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
            val colors = DonutChart.colors(valued.size)
            return valued.mapIndexed { index, (key, value) -> Category(key, PvFormat.networthCategory(key), value, colors[index]) }
        }

        private const val CATEGORY_ROW_H = 20f
        private const val CATEGORY_DOT = 3f
        private const val CHART_PANE_MAX_W = 176f
        private const val CHART_PANE_PORTION = 0.42f
        private const val CHART_DIAMETER = 140f
        private const val CENTER_TEXT_PORTION = 0.62f
    }
}
