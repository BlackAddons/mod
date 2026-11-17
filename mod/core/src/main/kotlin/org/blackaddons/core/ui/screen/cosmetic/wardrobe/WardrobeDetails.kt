package org.blackaddons.core.ui.screen.cosmetic.wardrobe

import org.blackaddons.api.ui.layout.Rect
import org.blackaddons.api.ui.layout.hit
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.Tooltip
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.InputTarget
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.MouseDragInput
import org.blackaddons.api.ui.widget.MouseScrollInput
import org.blackaddons.api.ui.widget.ScrollableList
import org.blackaddons.api.ui.widget.Slider
import org.blackaddons.api.util.I18n
import org.blackaddons.core.cosmetics.mesh.AdjustAxis
import org.blackaddons.core.cosmetics.mesh.Cosmetic3dEntry
import org.blackaddons.core.cosmetics.mesh.CosmeticAdjust
import org.blackaddons.core.cosmetics.mesh.ModifierAxis
import org.blackaddons.core.cosmetics.mesh.ModifierOption
import org.blackaddons.core.ui.screen.cosmetic.drawPill
import java.util.Locale

/**
 * Controls for the selection: variant axes, tint slots and placement axes. Reads and writes the [WardrobeDraft], not
 * stored state. Rows wrap instead of squeezing.
 */
internal class WardrobeDetails(
    private val draft: WardrobeDraft,
    private val onEditTint: (slot: String, swatch: Rect) -> Unit,
) : InputTarget {

    private sealed interface Detail {
        class Variants(val axis: ModifierAxis, val options: List<ModifierOption>, val labeled: Boolean) : Detail
        class Colors(val slots: List<String>, val labeled: Boolean) : Detail
        class Placement(val axis: AdjustAxis, val slider: Slider) : Detail
    }

    private val list = ScrollableList<Detail>().apply {
        rowHeight = DETAIL_ROW_H
        drawBackground = false
        drawRow = ::drawDetail
    }

    /** The slider the mouse was pressed on, so it keeps getting the drag after the pointer leaves it. */
    private var draggingSlider: Slider? = null

    // Row origin, so clicks hit-test controls where they were drawn.
    private var originX = 0f

    private var pointerX = 0f
    private var pointerY = 0f

    // Widest option label per axis. Cleared with the selection, because other cosmetics reuse axis ids with other options.
    private val axisLabelWidth = HashMap<String, Float>()

    private var entry: Cosmetic3dEntry? = null

    /** Calculates the rows for [entry] at width [w]. Call before [height] and [render]. */
    fun layout(entry: Cosmetic3dEntry?, w: Float) {
        this.entry = entry
        list.items = rowsFor(entry, w)
    }

    /** Enough for the rows, but never more than half the column, so the catalog stays browsable. */
    fun height(available: Float): Float {
        if (list.items.isEmpty()) return 0f
        val needed = list.items.size * (DETAIL_ROW_H + list.rowGap)
        return minOf(needed, available * MAX_DETAILS_FRACTION)
    }

    fun render(x: Float, y: Float, w: Float, h: Float, mouseX: Float, mouseY: Float) {
        pointerX = mouseX
        pointerY = mouseY
        originX = x
        list.boxWidth = w
        list.boxHeight = h
        list.render(x, y, w, h)
    }

    /** Hidden this frame: drops rows and bounds, so nothing that isn't drawn stays clickable. */
    fun clear() {
        list.items = emptyList()
        list.clear()
    }

    fun onSelectionChanged() {
        axisLabelWidth.clear()
        draggingSlider = null
        list.items = emptyList()
        list.resetScroll()
    }

    override fun mouseClicked(mouse: MouseButtonInput): Boolean {
        if (!mouse.isLeft) return false
        val row = list.rowAt(mouse.x, mouse.y)
        val detail = list.items.getOrNull(row) ?: return false
        val rowY = list.rowTop(row)
        val mx = mouse.x
        val my = mouse.y
        val selected = entry ?: return true

        when (detail) {
            is Detail.Variants -> {
                val rects = optionRects(detail.axis, detail.options.size, originX, rowY, list.boxWidth)
                for ((i, option) in detail.options.withIndex()) {
                    if (rects[i].has(mx, my)) {
                        draft.setModifier(detail.axis.id, option.id, selected)
                        return true
                    }
                }
            }
            is Detail.Colors -> {
                val rects = tintRects(detail.slots.size, originX, rowY)
                for ((i, slot) in detail.slots.withIndex()) {
                    if (rects[i].has(mx, my)) {
                        onEditTint(slot, rects[i])
                        return true
                    }
                }
            }
            is Detail.Placement -> {
                if (detail.slider.mouseClicked(mouse)) draggingSlider = detail.slider
            }
        }
        return true
    }

    /** True while a slider owns the drag, also if it ignored this delta, so the turntable behind it never spins. */
    override fun mouseDragged(drag: MouseDragInput): Boolean {
        val slider = draggingSlider ?: return false
        slider.mouseDragged(drag)
        return true
    }

    override fun mouseReleased(mouse: MouseButtonInput): Boolean {
        val slider = draggingSlider ?: return false
        slider.mouseReleased(mouse)
        draggingSlider = null
        return true
    }

    override fun mouseScrolled(wheel: MouseScrollInput): Boolean =
        list.mouseScrolled(wheel)

    private fun rowsFor(entry: Cosmetic3dEntry?, w: Float): List<Detail> {
        if (entry == null) return emptyList()
        // Conditional axes stay hidden until their dependency is met. The resolve would skip them anyway.
        val chosen = entry.axes.associate { it.id to draft.chosenOption(it) }
        val rows = ArrayList<Detail>(entry.axes.size + 1)
        for (axis in entry.axes.filter { it.isActive(chosen) }) {
            axis.options.chunked(pillsPerRow(axis, w)).forEachIndexed { i, chunk ->
                rows.add(Detail.Variants(axis, chunk, labeled = i == 0))
            }
        }

        val slots = draft.tintSlots()
        if (slots.isNotEmpty()) {
            val perRow = ((w + TINT_GAP) / (TINT_CELL + TINT_GAP)).toInt().coerceAtLeast(1)
            slots.chunked(perRow).forEachIndexed { i, chunk -> rows.add(Detail.Colors(chunk, labeled = i == 0)) }
        }

        // One row per axis, because a slider needs the width.
        for (axis in entry.adjustAxes) {
            draft.sliderFor(axis.id)?.let { rows.add(Detail.Placement(axis, it)) }
        }
        return rows
    }

    private fun drawDetail(detail: Detail, index: Int, rowX: Float, rowY: Float, rowW: Float) {
        when (detail) {
            is Detail.Variants -> {
                if (detail.labeled) {
                    Render.text(detail.axis.label, rowX, rowY, TextStyle.SMALL_SECONDARY)
                }
                val chosen = draft.chosenOption(detail.axis)
                val rects = optionRects(detail.axis, detail.options.size, rowX, rowY, rowW)
                for ((i, option) in detail.options.withIndex()) {
                    val rect = rects[i]
                    val label = Render.ellipsize(option.label, rect.w - 6f, Theme.FONT_SMALL, Fonts.medium)
                    drawPill(rect, label, option.id == chosen, pointerX, pointerY)
                    Tooltip.showOver(rect, option.label.takeIf { it != label })
                }
            }
            is Detail.Colors -> {
                if (detail.labeled) {
                    Render.text(
                        I18n.trOr("blackaddons.wardrobe.colors", "Colors"),
                        rowX, rowY, TextStyle.SMALL_SECONDARY,
                    )
                }
                val rects = tintRects(detail.slots.size, rowX, rowY)
                for ((i, slot) in detail.slots.withIndex()) {
                    val rect = rects[i]
                    val hovered = hit(pointerX, pointerY, rect.x, rect.y, rect.w, rect.h)
                    Render.rect(rect, draft.tintColor(slot), Theme.RADIUS_SMALL)
                    Render.hollowRect(
                        rect.x, rect.y, rect.w, rect.h, 1f,
                        if (hovered) Theme.ACCENT else Theme.BORDER,
                        Theme.RADIUS_SMALL,
                    )
                    Render.text(
                        slot,
                        rect.x + rect.w + 4f, Render.centerY(rect.y, rect.h, TextStyle.SMALL_MUTED),
                        TextStyle.SMALL_MUTED, maxWidth = TINT_LABEL_W,
                    )
                }
            }
            is Detail.Placement -> {
                Render.text(
                    axisLabel(detail.axis), rowX, rowY,
                    TextStyle.SMALL_SECONDARY,
                )
                val readout = axisReadout(detail.axis, detail.slider.value)
                Render.textRight(
                    readout, rowX + rowW, rowY,
                    TextStyle.SMALL_MUTED,
                )
                detail.slider.render(rowX, rowY + LABEL_H, rowW, PILL_H, pointerX, pointerY)
            }
        }
    }

    /** One width per axis: the widest label, padded. Long labels mean fewer per row, not cut text. */
    private fun pillWidth(axis: ModifierAxis, rowW: Float): Float {
        val widest = axisLabelWidth.getOrPut(axis.id) {
            axis.options.maxOfOrNull { Render.textWidth(it.label, Theme.FONT_SMALL, Fonts.medium) } ?: 0f
        }
        return (widest + PILL_TEXT_PAD).coerceIn(PILL_MIN_W, rowW)
    }

    private fun pillsPerRow(axis: ModifierAxis, rowW: Float): Int =
        ((rowW + PILL_GAP) / (pillWidth(axis, rowW) + PILL_GAP)).toInt().coerceAtLeast(1)

    private fun optionRects(axis: ModifierAxis, count: Int, rowX: Float, rowY: Float, rowW: Float): List<Rect> {
        val width = pillWidth(axis, rowW)
        val y = rowY + LABEL_H
        return List(count) { i -> Rect().apply { set(rowX + i * (width + PILL_GAP), y, width, PILL_H) } }
    }

    // Every color row keeps the label offset, so swatches line up as one grid.
    private fun tintRects(count: Int, rowX: Float, rowY: Float): List<Rect> {
        val y = rowY + LABEL_H
        return List(count) { i -> Rect().apply { set(rowX + i * (TINT_CELL + TINT_GAP), y, SWATCH, PILL_H) } }
    }

    private companion object {
        const val DETAIL_ROW_H = 40f
        const val LABEL_H = 16f
        const val PILL_H = 20f
        const val PILL_GAP = 4f
        const val PILL_MIN_W = 58f
        const val PILL_TEXT_PAD = 14f
        const val SWATCH = 22f
        const val TINT_LABEL_W = 52f

        /** A swatch plus its label and the gap to the next. This grid step decides wrapping. */
        const val TINT_CELL = SWATCH + 4f + TINT_LABEL_W
        const val TINT_GAP = 8f

        /** Max height of the options panel, so a cosmetic with many axes can't take the catalog's space. */
        const val MAX_DETAILS_FRACTION = 0.5f

        fun axisLabel(axis: AdjustAxis): String = when (axis.id) {
            CosmeticAdjust.SCALE -> I18n.trOr("blackaddons.wardrobe.adjust.scale", "Size")
            "offsetX" -> I18n.trOr("blackaddons.wardrobe.adjust.offsetX", "Left / right")
            "offsetY" -> I18n.trOr("blackaddons.wardrobe.adjust.offsetY", "Up / down")
            else -> I18n.trOr("blackaddons.wardrobe.adjust.offsetZ", "Front / back")
        }

        /** Scale as a multiplier, offsets signed. Locale.ROOT avoids "1,25x" on ru clients. */
        fun axisReadout(axis: AdjustAxis, value: Double): String =
            if (axis.id == CosmeticAdjust.SCALE) String.format(Locale.ROOT, "%.2f×", value)
            else String.format(Locale.ROOT, "%+.2f", value)
    }
}
