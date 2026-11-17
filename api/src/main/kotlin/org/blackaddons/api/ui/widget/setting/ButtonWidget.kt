package org.blackaddons.api.ui.widget.setting

import org.blackaddons.api.feature.settings.impl.ButtonSetting
import org.blackaddons.api.feature.translatedLabel
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.MouseButtonInput

class ButtonWidget(setting: ButtonSetting) : SettingWidget(setting) {

    private val btn = setting

    private val text: String get() = btn.translatedLabel

    private val button = Button(text) { btn.action() }

    override val naturalWidth: Float
        get() {
            button.label = text
            return button.naturalWidth().coerceIn(MIN_WIDTH, MAX_WIDTH)
        }

    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float) {
        button.label = text
        bounds.set(x, y + INSET, widgetWidth, BUTTON_HEIGHT)
        button.render(x, y + INSET, widgetWidth, BUTTON_HEIGHT, mouseX, mouseY)
    }

    override fun mouseClicked(mouse: MouseButtonInput): Boolean =
        this.button.mouseClicked(mouse)

    private companion object {
        const val MIN_WIDTH = 56f

        const val INSET = 2f

        const val BUTTON_HEIGHT = Theme.SETTING_ROW_HEIGHT - INSET * 2f
    }
}
