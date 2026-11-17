package org.blackaddons.api.ui.widget

import org.blackaddons.api.ui.layout.centered
import org.blackaddons.api.ui.layout.hit
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha

class MovementKeybindSelector(
    initialSelection: List<String> = emptyList(),
    var onChange: (List<String>) -> Unit = {},
) : Widget {

    data class KeyChip(val id: String, val label: String, val detail: String)

    private val selected = LinkedHashSet<String>(initialSelection)

    private var lastX = 0f
    private var lastY = 0f
    override var width: Float = 300f
    override val height: Float get() = 4 * 26f + 3 * Theme.PADDING_SMALL

    override fun draw(x: Float, y: Float) = render(x, y, width, UiMouse.x, UiMouse.y)

    fun render(x: Float, y: Float, w: Float, mouseX: Float, mouseY: Float) {
        lastX = x
        lastY = y
        width = w
        val gap = Theme.PADDING_SMALL
        val chipH = 26f
        val thirdW = (w - gap * 2f) / 3f
        val halfW = (w - gap) / 2f

        var cy = y

        val fwdX = x + thirdW + gap
        drawChip(FORWARD_CHIP, fwdX, cy, thirdW, chipH, mouseX, mouseY)
        cy += chipH + gap

        drawChip(LEFT_CHIP, x, cy, thirdW, chipH, mouseX, mouseY)
        drawChip(BACKWARD_CHIP, x + thirdW + gap, cy, thirdW, chipH, mouseX, mouseY)
        drawChip(RIGHT_CHIP, x + (thirdW + gap) * 2f, cy, thirdW, chipH, mouseX, mouseY)
        cy += chipH + gap

        drawChip(JUMP_CHIP, x, cy, w, chipH, mouseX, mouseY)
        cy += chipH + gap

        drawChip(SPRINT_CHIP, x, cy, halfW, chipH, mouseX, mouseY)
        drawChip(SHIFT_CHIP, x + halfW + gap, cy, halfW, chipH, mouseX, mouseY)
    }

    private fun drawChip(chip: KeyChip, cx: Float, cy: Float, cw: Float, ch: Float, mx: Float, my: Float) {
        val isSelected = selected.contains(chip.id)
        val isHovered = hit(mx, my, cx, cy, cw, ch)

        val bg = when {
            isSelected -> Theme.ACCENT.withAlpha(200)
            isHovered -> Theme.SURFACE_HOVER
            else -> Theme.SURFACE
        }

        Render.rect(cx, cy, cw, ch, bg, Theme.RADIUS_SMALL)
        if (isSelected) {
            Render.hollowRect(cx, cy, cw, ch, 1.5f, Theme.ACCENT, Theme.RADIUS_SMALL)
        }

        val text = "${chip.label} (${chip.detail})"
        val textW = Render.textWidth(text, Theme.FONT_SMALL, Fonts.medium)
        val textX = centered(cx, cw, textW)
        val textY = Render.centerY(cy, ch, Theme.FONT_SMALL, Fonts.medium)
        val textColor = if (isSelected) Theme.TEXT_PRIMARY else Theme.TEXT_MUTED
        Render.text(text, textX, textY, Theme.FONT_SMALL, textColor, Fonts.medium)
    }

    override fun mouseClicked(mouse: MouseButtonInput): Boolean {
        if (!mouse.isLeft) return false
        val mx = mouse.x
        val my = mouse.y

        val gap = Theme.PADDING_SMALL
        val chipH = 26f
        val thirdW = (width - gap * 2f) / 3f
        val halfW = (width - gap) / 2f

        val chipsWithBounds = listOf(
            FORWARD_CHIP to Pair(lastX + thirdW + gap, lastY to thirdW),
            LEFT_CHIP to Pair(lastX, lastY + chipH + gap to thirdW),
            BACKWARD_CHIP to Pair(lastX + thirdW + gap, lastY + chipH + gap to thirdW),
            RIGHT_CHIP to Pair(lastX + (thirdW + gap) * 2f, lastY + chipH + gap to thirdW),
            JUMP_CHIP to Pair(lastX, lastY + (chipH + gap) * 2f to width),
            SPRINT_CHIP to Pair(lastX, lastY + (chipH + gap) * 3f to halfW),
            SHIFT_CHIP to Pair(lastX + halfW + gap, lastY + (chipH + gap) * 3f to halfW)
        )

        for ((chip, bounds) in chipsWithBounds) {
            val cx = bounds.first
            val cy = bounds.second.first
            val cw = bounds.second.second
            if (hit(mx, my, cx, cy, cw, chipH)) {
                if (selected.contains(chip.id)) {
                    selected.remove(chip.id)
                } else {
                    selected.add(chip.id)
                }
                onChange(selected.toList())
                return true
            }
        }
        return false
    }

    companion object {
        val FORWARD_CHIP = KeyChip("forward", "Forward", "W")
        val LEFT_CHIP = KeyChip("left", "Left", "A")
        val BACKWARD_CHIP = KeyChip("backward", "Backward", "S")
        val RIGHT_CHIP = KeyChip("right", "Right", "D")
        val JUMP_CHIP = KeyChip("jump", "Jump", "SPACE")
        val SPRINT_CHIP = KeyChip("sprint", "Sprint", "CTRL")
        val SHIFT_CHIP = KeyChip("shift", "Shift", "SHIFT")

        /** Every chip, in reading order. */
        val CHIPS = listOf(FORWARD_CHIP, LEFT_CHIP, BACKWARD_CHIP, RIGHT_CHIP, JUMP_CHIP, SPRINT_CHIP, SHIFT_CHIP)
    }
}
