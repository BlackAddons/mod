package org.blackaddons.core.ui.screen.cosmetic

import org.blackaddons.api.ui.layout.Align
import org.blackaddons.api.ui.layout.LayoutNode
import org.blackaddons.api.ui.layout.Rect
import org.blackaddons.api.ui.layout.column
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.widget.InputTarget
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.MouseDragInput
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.formatDecimals
import org.blackaddons.core.cosmetics.me.CosmeticDraft
import org.blackaddons.core.cosmetics.mesh.MIN_SIDE_SCALE

/** Model scale per axis, one `X ----o---- 1.00` row per axis. [onEdit] clears the save outcome. */
internal class ScaleEditor(
    private val draft: () -> CosmeticDraft?,
    private val onEdit: () -> Unit,
) : InputTarget {

    private val sliders = Array(3) { Rect() }
    private var dragging = -1

    fun render(x: Float, y: Float, w: Float, d: CosmeticDraft): Float {
        val node = block(w, d)
        node.draw(x, y)
        return y + node.height
    }

    override fun mouseClicked(mouse: MouseButtonInput): Boolean {
        val d = draft() ?: return false
        if (!mouse.isLeft) return false
        val mx = mouse.x
        for (i in sliders.indices) if (sliders[i].has(mouse.x, mouse.y)) {
            dragging = i
            setAxis(d, i, mx)
            return true
        }
        return false
    }

    override fun mouseDragged(drag: MouseDragInput): Boolean {
        val d = draft() ?: return false
        if (dragging < 0) return false
        setAxis(d, dragging, drag.x)
        return true
    }

    override fun mouseReleased(mouse: MouseButtonInput): Boolean {
        dragging = -1
        return false
    }

    private fun block(w: Float, d: CosmeticDraft): LayoutNode {
        val sliderW = w - LABEL_W - VALUE_W - GAP * 2f
        val axes = listOf("X" to d.scaleX, "Y" to d.scaleY, "Z" to d.scaleZ)
        return column(spacing = ROW_GAP) {
            text(
                I18n.trOr("blackaddons.cosmetic_editor.scale", "Scale"),
                TextStyle.SMALL_SECONDARY,
            )
            for ((i, axis) in axes.withIndex()) {
                val (label, value) = axis
                row(spacing = GAP, align = Align.CENTER, width = w) {
                    cell(LABEL_W, SLIDER_H) { cx, cy ->
                        Render.text(
                            label, cx, Render.centerY(cy, SLIDER_H, TextStyle.SMALL_MUTED),
                            TextStyle.SMALL_MUTED,
                        )
                    }
                    cell(sliderW, SLIDER_H) { cx, cy ->
                        sliders[i].set(cx, cy, sliderW, SLIDER_H)
                        drawSlider(sliders[i], frac(value, axisMin(i)), true)
                    }
                    cell(VALUE_W, SLIDER_H) { cx, cy ->
                        Render.text(
                            value.formatDecimals(), cx,
                            Render.centerY(cy, SLIDER_H, TextStyle.SMALL_SECONDARY),
                            TextStyle.SMALL_SECONDARY,
                        )
                    }
                }
            }
        }
    }

    private fun setAxis(d: CosmeticDraft, axis: Int, mx: Float) {
        val min = axisMin(axis)
        val v = snapToStep(min + fracFromMouse(sliders[axis], mx) * (MAX - min))
        when (axis) {
            0 -> d.scaleX = v
            1 -> d.scaleY = v
            else -> d.scaleZ = v
        }
        onEdit()
    }

    private fun frac(v: Float, min: Float) = ((v - min) / (MAX - min)).coerceIn(0f, 1f)

    // Only the height can go negative (upside down). Width and depth stop at flat, see withoutSideMirror.
    private fun axisMin(axis: Int) = if (axis == 1) MIN_HEIGHT else MIN_SIDE_SCALE

    private companion object {
        const val LABEL_W = 10f
        const val VALUE_W = 32f
        const val GAP = 6f
        const val ROW_GAP = 16f
        const val SLIDER_H = 8f

        const val MIN_HEIGHT = -2f
        const val MAX = 2f
    }
}
