package org.blackaddons.core.ui.screen.actions

import org.blackaddons.api.ui.render.Font
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.WidgetHost
import java.awt.Color

internal enum class RowButton {
    DELETE, STEPS, EDIT, TOGGLE,
}

/** The row button strip, drawn and hit-tested from one geometry. Group rows have no STEPS (`withSteps`). */
internal object ActionRowButtons {

    private const val BUTTON_H = 20f
    private const val TOP_INSET = 4f
    private const val BUTTON_W = 48f
    private const val TOGGLE_W = 52f

    // Left edges, measured back from the row's right edge.
    private val FULL = mapOf(
        RowButton.DELETE to 225f,
        RowButton.STEPS to 170f,
        RowButton.EDIT to 115f,
        RowButton.TOGGLE to 60f,
    )
    private val WITHOUT_STEPS = mapOf(
        RowButton.DELETE to 170f,
        RowButton.EDIT to 115f,
        RowButton.TOGGLE to 60f,
    )

    private fun offsets(withSteps: Boolean) = if (withSteps) FULL else WITHOUT_STEPS

    private fun widthOf(button: RowButton) = if (button == RowButton.TOGGLE) TOGGLE_W else BUTTON_W

    fun xOf(button: RowButton, rx: Float, rw: Float, withSteps: Boolean = true): Float =
        rx + rw - (offsets(withSteps)[button] ?: 0f)

    fun stripLeft(rx: Float, rw: Float, withSteps: Boolean = true): Float =
        rx + rw - offsets(withSteps).values.max()

    fun topOf(ry: Float): Float = ry + TOP_INSET

    val height: Float get() = BUTTON_H

    fun draw(
        widgets: WidgetHost, rx: Float, ry: Float, rw: Float, enabled: Boolean, withSteps: Boolean = true,
        onPress: (RowButton) -> Unit,
    ) {
        for (button in offsets(withSteps).keys) {
            val x = xOf(button, rx, rw, withSteps)
            val w = widthOf(button)
            val hovered = widgets.clickable(x, topOf(ry), w, BUTTON_H) { onPress(button) }
            when (button) {
                RowButton.DELETE -> draw(x, ry, w, actionTr("delete", "Delete"), if (hovered) Theme.DANGER else Theme.SURFACE_ACTIVE, Fonts.medium)
                RowButton.STEPS -> draw(x, ry, w, actionTr("steps.button", "Steps"), if (hovered) Theme.ACCENT else Theme.SURFACE_ACTIVE, Fonts.medium)
                RowButton.EDIT -> draw(x, ry, w, actionTr("edit", "Edit"), if (hovered) Theme.ACCENT else Theme.SURFACE_ACTIVE, Fonts.medium)
                RowButton.TOGGLE -> {
                    val resting = if (enabled) Theme.ACCENT else Theme.SURFACE_ACTIVE
                    draw(x, ry, w, actionTr(if (enabled) "on" else "off", if (enabled) "ON" else "OFF"), if (hovered) Theme.ACCENT.withAlpha(200) else resting, Fonts.bold)
                }
            }
        }
    }

    private fun draw(x: Float, ry: Float, w: Float, label: String, fill: Color, font: Font) {
        val y = topOf(ry)
        Render.rect(x, y, w, BUTTON_H, fill, Theme.RADIUS_SMALL)
        Render.textCentered(
            label, x + w / 2f,
            Render.centerY(y, BUTTON_H, Theme.FONT_SMALL, font),
            Theme.FONT_SMALL, Theme.TEXT_PRIMARY, font,
            maxWidth = w - 6f,
        )
    }
}
