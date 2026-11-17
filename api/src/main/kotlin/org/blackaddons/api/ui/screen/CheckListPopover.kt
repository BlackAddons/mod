package org.blackaddons.api.ui.screen

import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.Scroll
import org.blackaddons.api.ui.widget.dropdown.checkRowWidth
import org.blackaddons.api.ui.widget.dropdown.drawCheckRow

internal class CheckListPopover(
    private val items: List<Pair<String, String>>,
    private val checked: (String) -> Boolean,
    private val onToggle: (String) -> Unit,
    private val anchorX: Float,
    private val anchorY: Float,
    private val anchorW: Float,
    private val anchorH: Float,
) : Popover() {

    private val scroll = widgets.add(Scroll())

    override fun render(mouseX: Float, mouseY: Float, deltaTicks: Float) {
        val screenW = UiManager.screenWidth
        val screenH = UiManager.screenHeight
        val full = items.size * ROW_H
        val w = maxOf(anchorW, items.maxOfOrNull { checkRowWidth(it.second) + Scroll.LANE_WIDTH } ?: 0f)
            .coerceAtMost(screenW - MARGIN * 2f)
        val below = screenH - MARGIN - (anchorY + anchorH)
        val above = anchorY - MARGIN
        val h = minOf(full, MAX_ROWS * ROW_H, maxOf(below, above, ROW_H))
        val y = if (h <= below || below >= above) anchorY + anchorH else anchorY - h
        val x = anchorX.coerceIn(MARGIN, maxOf(MARGIN, screenW - w - MARGIN))
        card.set(x, y, w, h)

        drawCard(radius = Theme.RADIUS_SMALL, border = Theme.BORDER.withAlpha(220), borderWidth = 1.5f) {
            scroll.render(x, y, w, h, full) { offset ->
                val rowW = if (full > h) w - Scroll.LANE_WIDTH - 2f else w
                items.forEachIndexed { i, (id, label) ->
                    val rowY = y + i * ROW_H - offset
                    val top = maxOf(rowY, y)
                    val bottom = minOf(rowY + ROW_H, y + h)
                    if (bottom <= top) return@forEachIndexed
                    if (widgets.clickable(x, top, rowW, bottom - top) { onToggle(id) }) {
                        Render.rect(x, rowY, rowW, ROW_H, Theme.SURFACE_HOVER, Theme.RADIUS_SMALL)
                    }
                    drawCheckRow(label, checked(id), x, rowY, rowW, ROW_H)
                }
            }
        }
    }

    private companion object {
        const val ROW_H = 20f
        const val MAX_ROWS = 10
    }
}
