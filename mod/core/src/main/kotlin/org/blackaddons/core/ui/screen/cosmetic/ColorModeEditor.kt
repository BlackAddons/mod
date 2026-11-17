package org.blackaddons.core.ui.screen.cosmetic

import org.blackaddons.api.ui.layout.Rect
import org.blackaddons.api.ui.layout.columnWidth
import org.blackaddons.api.ui.layout.row
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.Panel
import org.blackaddons.api.ui.screen.color.ColorPopover
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.InputTarget
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.MouseDragInput
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.color.ColorStop
import org.blackaddons.core.cosmetics.GradientStops
import org.blackaddons.core.cosmetics.me.ColorMode
import org.blackaddons.core.cosmetics.me.CosmeticDraft
import java.awt.Color

private val tr = I18n.scope("blackaddons.cosmetic_editor")

internal class ColorModeEditor(
    private val draft: () -> CosmeticDraft?,
    private val openPopover: (Panel) -> Unit,
    private val onEdit: () -> Unit,
) : InputTarget {

    private val gradient = GradientEditor(draft = draft, openPopover = openPopover, onEdit = onEdit)

    private val pills = Array(ColorMode.entries.size) { Rect() }
    private val solidSwatch = Rect()
    private val speedSlider = Rect()
    private var draggingSpeed = false

    fun render(x: Float, y: Float, w: Float, d: CosmeticDraft, mouseX: Float, mouseY: Float): Float {
        // Only two modes have a speed slider. A stale rect would stay clickable.
        speedSlider.set(0f, 0f, 0f, 0f)

        val modes = ColorMode.entries
        val pillW = columnWidth(w, modes.size, PILL_GAP)
        row(spacing = PILL_GAP, width = w) {
            for ((i, mode) in modes.withIndex()) {
                cell(pillW, PILL_H) { cx, cy ->
                    pills[i].set(cx, cy, pillW, PILL_H)
                    drawPill(pills[i], label(mode), d.mode == mode, mouseX, mouseY)
                }
            }
        }.draw(x, y)

        var cy = y + PILL_H + Theme.PADDING_LARGE
        when (d.mode) {
            ColorMode.VANILLA -> {
                Render.text(
                    tr("vanilla_hint", "No color override."),
                    x, cy, TextStyle.SMALL_MUTED,
                )
                cy += Render.textHeight(Theme.FONT_SMALL, Fonts.regular)
            }
            ColorMode.SOLID -> {
                Render.text(
                    tr("color", "Color"),
                    x, cy, TextStyle.SMALL_SECONDARY,
                )
                solidSwatch.set(x, cy + labelAdvance(), SWATCH_W, SWATCH_H)
                Render.rect(solidSwatch, Color(d.rgb and 0xFFFFFF), Theme.RADIUS_SMALL)
                Render.hollowRect(solidSwatch, 1f, Theme.BORDER, Theme.RADIUS_SMALL)
                Render.text(
                    "#%06X".format(d.rgb and 0xFFFFFF),
                    solidSwatch.x + solidSwatch.w + Theme.PADDING,
                    Render.centerY(solidSwatch.y, solidSwatch.h, TextStyle.SMALL_SECONDARY),
                    TextStyle.SMALL_SECONDARY,
                )
                cy = solidSwatch.y + solidSwatch.h
            }
            ColorMode.GRADIENT -> {
                cy = gradient.render(x, cy, w, d, mouseX, mouseY)
                if (d.animated) cy = renderSpeed(x, cy, w, d.speed, speedSlider)
            }
            ColorMode.CHROMA -> cy = renderSpeed(x, cy, w, d.speed, speedSlider)
        }
        return cy
    }

    override fun mouseClicked(mouse: MouseButtonInput): Boolean {
        val d = draft() ?: return false
        if (!mouse.isLeft) return false
        val mx = mouse.x
        val my = mouse.y
        for (i in pills.indices) if (pills[i].has(mx, my)) {
            d.mode = ColorMode.entries[i]
            // The backend needs at least two stops.
            if (d.mode == ColorMode.GRADIENT && d.stops.size < GradientStops.MIN_STOPS) {
                d.stops.clear()
                d.stops += ColorStop(Theme.BRAND_FROM.rgb and 0xFFFFFF, 0f)
                d.stops += ColorStop(Theme.BRAND_TO.rgb and 0xFFFFFF, 1f)
            }
            onEdit()
            return true
        }

        // Gated on the mode, because only its controls were drawn.
        when (d.mode) {
            ColorMode.SOLID -> if (solidSwatch.has(mx, my)) {
                openPopover(
                    ColorPopover(
                        Color(d.rgb and 0xFFFFFF),
                        anchorX = solidSwatch.x, anchorY = solidSwatch.y, anchorW = solidSwatch.w, anchorH = solidSwatch.h,
                    ) {
                        d.rgb = it.rgb and 0xFFFFFF
                        onEdit()
                    },
                )
                return true
            }
            ColorMode.GRADIENT -> if (gradient.mouseClicked(mouse)) return true
            else -> {}
        }

        if (speedSlider.w > 0f && speedSlider.has(mx, my)) {
            draggingSpeed = true
            setSpeed(d, mx)
            return true
        }
        return false
    }

    override fun mouseDragged(drag: MouseDragInput): Boolean {
        val d = draft() ?: return false
        if (gradient.mouseDragged(drag)) return true
        if (!draggingSpeed) return false
        setSpeed(d, drag.x)
        return true
    }

    override fun mouseReleased(mouse: MouseButtonInput): Boolean {
        draggingSpeed = false
        return gradient.mouseReleased(mouse)
    }

    private fun setSpeed(d: CosmeticDraft, mx: Float) {
        d.speed = speedValue(fracFromMouse(speedSlider, mx))
        onEdit()
    }

    private fun label(mode: ColorMode): String = when (mode) {
        ColorMode.VANILLA -> tr("mode_vanilla", "Vanilla")
        ColorMode.SOLID -> tr("mode_solid", "Solid")
        ColorMode.GRADIENT -> tr("mode_gradient", "Gradient")
        ColorMode.CHROMA -> tr("mode_chroma", "Chroma")
    }

    private companion object {
        const val PILL_GAP = 6f
        const val PILL_H = 20f

        const val SWATCH_W = 64f
        const val SWATCH_H = 22f
    }
}
