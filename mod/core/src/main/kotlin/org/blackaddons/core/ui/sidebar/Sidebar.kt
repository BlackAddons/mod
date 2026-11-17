package org.blackaddons.core.ui.sidebar

import org.blackaddons.api.ui.anim.Animation
import org.blackaddons.api.ui.anim.Easing
import org.blackaddons.api.ui.anim.isVisible
import org.blackaddons.api.ui.layout.centered
import org.blackaddons.api.ui.layout.hit
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.Tooltip
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.InputTarget
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.MouseDragInput
import org.blackaddons.api.ui.widget.MouseScrollInput
import org.blackaddons.api.ui.widget.Scroll
import org.blackaddons.api.util.I18n
import org.blackaddons.core.ui.sidebar.Sidebar.Companion.MAX_WIDTH
import org.blackaddons.core.ui.tab.DividerTab
import org.blackaddons.core.ui.tab.HeaderTab
import org.blackaddons.core.ui.tab.Tab
import org.blackaddons.core.ui.tab.TriggerTab
import java.awt.Color

internal class Sidebar(
    private val tabs: List<Tab>,
    private val onTabClick: (Int) -> Unit,
) : InputTarget {
    // Rows have different heights (dividers are thin), so positions are a prefix sum, built once.
    private val rowTops = FloatArray(tabs.size)
    private val contentHeight: Float

    init {
        var y = 0f
        tabs.forEachIndexed { i, tab ->
            rowTops[i] = y
            y += rowHeight(tab)
        }
        contentHeight = y
    }

    /**
     * Fits the longest label within [Theme.SIDEBAR_WIDTH]..[MAX_WIDTH]. Longer labels get an ellipsis and a hover.
     * Lazy, because tabs are fixed for the sidebar's whole life.
     */
    val width: Float by lazy {
        val chrome = LABEL_INSET + ICON_SIZE + ICON_GAP + LABEL_END_PAD + (Theme.SIDEBAR_WIDTH - contentRight(0f, Theme.SIDEBAR_WIDTH))
        val widest = tabs.filter { it.isSelectable }.maxOfOrNull { Render.textWidth(labelOf(it), Theme.FONT_BODY, Fonts.medium) } ?: 0f
        (widest + chrome).coerceIn(Theme.SIDEBAR_WIDTH, MAX_WIDTH)
    }

    var selectedIndex: Int = 0
        set(value) {
            field = value.coerceIn(0, tabs.lastIndex)
            selectionAnim.animateTo(rowTops[field])
        }

    // Animates Y directly. Index times a fixed height breaks with mixed row heights.
    private val selectionAnim = Animation(0f, Theme.ANIM_NORMAL.toLong(), Easing.easeOutCubic)
    private val hoverAnims = HashMap<Int, Animation>().apply {
        tabs.indices.forEach { put(it, Animation(0f, Theme.ANIM_HOVER.toLong())) }
    }

    /*
     * Only rows and the highlight scroll. Hit tests read the eased offset, so clicks during a glide hit what's drawn.
     * No track, because it looks like a seam on the dark sidebar.
     */
    private val scroll = Scroll().apply { showTrack = false }
    private val scrollOffset: Float get() = scroll.offset
    private var viewX = 0f
    private var viewY = 0f
    private var viewW = 0f
    private var viewH = 0f

    fun render(x: Float, y: Float, width: Float, height: Float, mouseX: Float, mouseY: Float) {
        viewX = x; viewY = y; viewW = width; viewH = height

        val r = Theme.RADIUS_LARGE
        Render.rect(x, y, r * 2f, height, sidebarBg, r)
        Render.rect(x + r, y, width - r, height, sidebarBg)
        Render.rect(x + width - 1f, y + 12f, 1f, height - 24f, Theme.BORDER)

        val areaTop = y + Theme.PADDING
        val areaH = (height - Theme.PADDING * 2f).coerceAtLeast(0f)
        // Ends before the strip, so the bar gets its own column.
        scroll.update(x, areaTop, railRight(x, width) - x, areaH, contentHeight)

        Render.pushScissor(x, areaTop, width, areaH)

        val selY = areaTop - scrollOffset + selectionAnim.get()
        val pillX = x + PILL_INSET
        val pillW = (contentRight(x, width) - pillX).coerceAtLeast(0f)
        Render.rect(pillX, selY + 2f, pillW, Theme.TAB_HEIGHT - 4f, Theme.ACCENT_SOFT, Theme.RADIUS_SMALL)
        Render.rect(
            tickRight(x, width) - TICK_WIDTH, selY + 8f,
            TICK_WIDTH, Theme.TAB_HEIGHT - 16f, Theme.ACCENT, 1.5f,
        )

        for ((i, tab) in tabs.withIndex()) {
            val tabY = areaTop - scrollOffset + rowTops[i]

            if (tab is DividerTab) {
                renderDivider(x, tabY, width)
                continue
            }
            if (tab is HeaderTab) {
                renderHeader(tab, x, tabY, width)
                continue
            }

            val hovered = hit(mouseX, mouseY, x, tabY, width, Theme.TAB_HEIGHT)
            val isSelected = i == selectedIndex && tab !is TriggerTab

            val hoverAnim = hoverAnims.getValue(i)
            hoverAnim.animateTo(if (hovered && !isSelected) 1f else 0f)

            if (hoverAnim.get().isVisible && !isSelected) {
                Render.rect(
                    pillX, tabY + 2f, pillW, Theme.TAB_HEIGHT - 4f,
                    Theme.SURFACE_HOVER.withAlpha((hoverAnim.get() * 0.35f).coerceAtMost(0.35f)),
                    Theme.RADIUS_SMALL,
                )
            }

            val textColor: Color = when {
                isSelected -> Theme.ACCENT
                hovered -> Theme.TEXT_PRIMARY
                else -> Theme.TEXT_SECONDARY
            }

            // Icon gutter is reserved also without an icon, so labels line up.
            tab.icon?.let {
                val iconY = centered(tabY, Theme.TAB_HEIGHT, ICON_SIZE)
                Render.icon(it, x + LABEL_INSET, iconY, ICON_SIZE, textColor)
            }

            val fullLabel = labelOf(tab)
            val labelX = x + LABEL_INSET + ICON_SIZE + ICON_GAP
            val label = Render.ellipsize(fullLabel, contentRight(x, width) - LABEL_END_PAD - labelX, Theme.FONT_BODY, Fonts.medium)
            if (hovered && mouseY in areaTop..(areaTop + areaH) && label != fullLabel) Tooltip.show(fullLabel)
            Render.text(
                label,
                labelX,
                Render.centerY(tabY, Theme.TAB_HEIGHT, Theme.FONT_BODY, Fonts.medium),
                Theme.FONT_BODY,
                textColor,
                Fonts.medium,
            )
        }

        Render.popScissor()
        scroll.draw()
    }

    /** Addon name centered in a rule (`---- Extras ----`). It's the separator, so [TabRegistry] adds none before it. */
    private fun renderHeader(tab: HeaderTab, x: Float, tabY: Float, width: Float) {
        val left = x + LABEL_INSET
        val right = contentRight(x, width)
        val room = (right - left - (HEADER_RULE_MIN + HEADER_LABEL_GAP) * 2f).coerceAtLeast(0f)
        val label = Render.ellipsize(tab.label, room, Theme.FONT_SMALL, Fonts.medium)
        val labelW = Render.textWidth(label, Theme.FONT_SMALL, Fonts.medium)

        val labelX = (left + right) / 2f - labelW / 2f
        val cy = tabY + Theme.TAB_HEIGHT / 2f

        val leftRuleW = labelX - HEADER_LABEL_GAP - left
        if (leftRuleW > 0f) Render.rect(left, cy, leftRuleW, 1f, Theme.BORDER)

        val rightRuleX = labelX + labelW + HEADER_LABEL_GAP
        if (right > rightRuleX) Render.rect(rightRuleX, cy, right - rightRuleX, 1f, Theme.BORDER)

        Render.text(
            label, labelX,
            Render.centerY(tabY, Theme.TAB_HEIGHT, TextStyle.SMALL_MUTED_MEDIUM),
            TextStyle.SMALL_MUTED_MEDIUM,
        )
        Tooltip.showOver(labelX, tabY, labelW, Theme.TAB_HEIGHT, tab.label.takeIf { it != label })
    }

    private fun renderDivider(x: Float, tabY: Float, width: Float) {
        val lineW = (contentRight(x, width) - x - LABEL_INSET).coerceAtLeast(0f)
        Render.rect(x + LABEL_INSET, tabY + DIVIDER_HEIGHT / 2f, lineW, 1f, Theme.BORDER)
    }

    override fun mouseClicked(mouse: MouseButtonInput): Boolean {
        // A press on the thumb shouldn't select the tab behind it.
        if (scroll.mouseClicked(mouse)) return true
        if (mouse.x < viewX || mouse.x > viewX + viewW) return false
        if (mouse.y < viewY + Theme.PADDING || mouse.y > viewY + viewH - Theme.PADDING) return false
        val index = rowAt((mouse.y - viewY - Theme.PADDING + scrollOffset)) ?: return false
        if (!tabs[index].isSelectable) return false
        onTabClick(index)
        return true
    }

    override fun mouseDragged(drag: MouseDragInput): Boolean =
        scroll.mouseDragged(drag)

    override fun mouseReleased(mouse: MouseButtonInput): Boolean =
        scroll.mouseReleased(mouse)

    private fun rowAt(relY: Float): Int? {
        if (relY < 0f || relY >= contentHeight) return null
        return rowTops.indexOfLast { it <= relY }.takeIf { it >= 0 }
    }

    /** True if consumed. Hit-tests the whole strip, not the inset viewport, so the wheel works on the inset too. */
    override fun mouseScrolled(wheel: MouseScrollInput): Boolean {
        if (wheel.x < viewX || wheel.x > viewX + viewW) return false
        if (wheel.y < viewY || wheel.y > viewY + viewH) return false
        return scroll.scrollBy(wheel.amount)
    }

    private val sidebarBg = Color(8, 9, 14)

    private companion object {
        const val LABEL_INSET = 14f
        const val ICON_SIZE = 14f
        const val ICON_GAP = 6f
        const val DIVIDER_HEIGHT = 9f
        const val PILL_INSET = 6f
        const val LABEL_END_PAD = 6f

        /** Wider costs the content area too much. Use an ellipsis instead. */
        const val MAX_WIDTH = 190f

        fun labelOf(tab: Tab): String = I18n.trOr(tab.label, tab.label)

        const val HEADER_LABEL_GAP = 8f
        const val HEADER_RULE_MIN = 6f

        /**
         * Right edge, inward: border, scrollbar, selected tick, then the pill. Everything is measured from these lanes,
         * so nothing overlaps. The scrollbar lane stays reserved also without a bar, so the tick doesn't jump.
         */

        const val BORDER_LANE = 4f
        const val TICK_WIDTH = 3f
        const val RAIL_GAP = 3f
        const val TICK_GAP = 1f

        fun railRight(x: Float, width: Float): Float = x + width - BORDER_LANE

        fun tickRight(x: Float, width: Float): Float = railRight(x, width) - Scroll.LANE_WIDTH - RAIL_GAP

        fun contentRight(x: Float, width: Float): Float = tickRight(x, width) - TICK_WIDTH - TICK_GAP

        fun rowHeight(tab: Tab): Float = if (tab is DividerTab) DIVIDER_HEIGHT else Theme.TAB_HEIGHT

        val Tab.isSelectable: Boolean get() = this !is HeaderTab && this !is DividerTab
    }
}
