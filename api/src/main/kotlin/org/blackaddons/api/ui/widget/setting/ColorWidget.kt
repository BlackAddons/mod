package org.blackaddons.api.ui.widget.setting

import org.blackaddons.api.feature.settings.impl.ColorSetting
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.screen.color.ColorPopover
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.Swatch
import org.blackaddons.api.util.I18n

class ColorWidget(setting: ColorSetting) : SettingWidget(setting) {

    private val col = setting
    override val stretches: Boolean get() = false

    // Hex and "Chroma" swap places; size to the wider.
    override val naturalWidth: Float
        get() {
            val hex = Render.textWidth("#FFFFFF", Theme.FONT_SMALL, Fonts.regular)
            val chroma = Render.textWidth(I18n.trOr("blackaddons.colorpicker.chroma", "Chroma"), Theme.FONT_SMALL, Fonts.regular)
            return SWATCH_W + 6f + maxOf(hex, chroma) + CONTENT_PAD
        }

    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float) {
        val h = Theme.SETTING_ROW_HEIGHT - 4f
        val cy = y + 2f
        val swatchW = SWATCH_W
        bounds.set(x, cy, widgetWidth, h)

        // Live value, so chroma cycles in the swatch.
        Swatch.draw(x, cy, swatchW, h, col.value)

        val base = col.baseColor
        val label = if (col.chroma) I18n.trOr("blackaddons.colorpicker.chroma", "Chroma")
        else "#%02X%02X%02X".format(base.red, base.green, base.blue)
        Render.text(
            label,
            x + swatchW + 6f,
            Render.centerY(cy, h, TextStyle.SMALL_SECONDARY),
            TextStyle.SMALL_SECONDARY,
        )
    }

    private companion object {
        const val SWATCH_W = 24f
    }

    override fun mouseClicked(mouse: MouseButtonInput): Boolean {
        if (!mouse.isLeft || !bounds.has(mouse.x, mouse.y)) return false
        UiManager.push(
            ColorPopover(
                initial = col.baseColor,
                anchorX = bounds.x, anchorY = bounds.y, anchorW = bounds.w, anchorH = bounds.h,
                allowAlpha = col.allowAlpha,
                supportsChroma = true,
                supportsPerCharMode = col.textChroma,
                initialChroma = col.chroma,
                initialChromaSpeed = col.chromaSpeed,
                initialChromaMode = col.chromaMode,
                default = col.defaultValue,
                defaultChroma = col.defaultChroma,
                defaultChromaSpeed = col.defaultChromaSpeed,
                defaultChromaMode = col.defaultChromaMode,
                onChange = { newColor -> col.value = newColor },
                onChromaChange = { chroma, speed, mode ->
                    col.chroma = chroma
                    col.chromaSpeed = speed
                    col.chromaMode = mode
                },
            )
        )
        return true
    }
}
