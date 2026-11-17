package org.blackaddons.api.ui.widget.setting

import org.blackaddons.api.feature.settings.impl.KeybindSetting
import org.blackaddons.api.input.Keys
import org.blackaddons.api.ui.layout.centered
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.Tooltip
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.KeyInput
import org.blackaddons.api.ui.widget.MouseButtonInput

class KeybindWidget(setting: KeybindSetting) : SettingWidget(setting) {

    private val keybind = setting
    private var listening = false
    // "MOUSE MIDDLE" is the realistic worst case.
    override val naturalWidth: Float
        get() = (Render.textWidth(label(), Theme.FONT_SMALL, Fonts.medium) + CONTENT_PAD * 2f)
            .coerceIn(MIN_WIDTH, MAX_WIDTH)

    private fun label(): String = if (listening) "..." else keybind.displayName

    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float) {
        val h = Theme.SETTING_ROW_HEIGHT - 4f
        val cy = y + 2f
        bounds.set(x, cy, widgetWidth, h)
        val hovered = bounds.has(mouseX, mouseY)
        val bg = when {
            listening -> Theme.ACCENT
            hovered -> Theme.SURFACE_HOVER
            else -> Theme.SURFACE
        }
        Render.rect(x, cy, widgetWidth, h, bg, Theme.RADIUS_SMALL)

        val full = label()
        val text = Render.ellipsize(full, widgetWidth - CONTENT_PAD * 2f, Theme.FONT_SMALL, Fonts.medium)
        if (hovered && text != full) Tooltip.show(full)
        val tw = Render.textWidth(text, Theme.FONT_SMALL, Fonts.medium)
        Render.text(
            text,
            centered(x, widgetWidth, tw),
            Render.centerY(cy, h, Theme.FONT_SMALL, Fonts.medium),
            Theme.FONT_SMALL,
            if (listening) Theme.TEXT_PRIMARY else Theme.TEXT_SECONDARY,
            Fonts.medium,
        )
    }

    private companion object {
        const val MIN_WIDTH = 90f
    }

    override fun mouseClicked(mouse: MouseButtonInput): Boolean {
        // Before the bounds check: while listening the pointer is captured, so side buttons bind anywhere.
        if (listening) {
            keybind.bindMouse(mouse.button)
            listening = false
            return true
        }
        if (!bounds.has(mouse.x, mouse.y)) return false
        if (!mouse.isLeft) return false
        listening = true
        return true
    }

    override fun keyPressed(key: KeyInput): Boolean {
        if (!listening) return false
        if (key.code == Keys.ESCAPE) {
            keybind.unbind()
        } else {
            keybind.bindKeyboard(key.code)
        }
        listening = false
        return true
    }
}
