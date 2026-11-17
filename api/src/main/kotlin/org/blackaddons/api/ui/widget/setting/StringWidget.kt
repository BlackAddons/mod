package org.blackaddons.api.ui.widget.setting

import org.blackaddons.api.feature.settings.impl.StringSetting
import org.blackaddons.api.feature.translatedPlaceholder
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.CharInput
import org.blackaddons.api.ui.widget.KeyInput
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.MouseDragInput
import org.blackaddons.api.ui.widget.text.TextInput

/** [StringSetting] row around a [TextInput]. Enter commits, Esc reverts; rejected values turn red and keep focus. */
class StringWidget(setting: StringSetting) : SettingWidget(setting) {

    private val str = setting

    private val input = TextInput(
        placeholder = setting.translatedPlaceholder,
        maxLength = setting.maxLength,
    ).apply {
        fontSize = Theme.FONT_SMALL
        font = Fonts.regular
        // The row decides what an outside click means.
        blurOnClickOutside = false
        if (setting.isMasked) {
            hasEyeToggle = true
            isMasked = true
        }
        displayText = { str.value }
        onFocusGained = { text = str.value }
        onEnter = { commit() }
        onEscape = { revert() }
    }

    override val naturalWidth: Float get() = if (str.isMasked) 240f else 160f

    override var focused: Boolean
        get() = input.focused
        set(value) {
            input.focused = value
        }

    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float) {
        input.placeholder = str.translatedPlaceholder
        val h = Theme.SETTING_ROW_HEIGHT - ROW_INSET * 2f
        val cy = y + ROW_INSET
        bounds.set(x, cy, widgetWidth, h)
        input.render(x, cy, widgetWidth, h, mouseX, mouseY)
    }

    override fun mouseClicked(mouse: MouseButtonInput): Boolean {
        if (!mouse.isLeft) return false
        if (bounds.has(mouse.x, mouse.y)) return input.mouseClicked(mouse)
        // Click-away is not an edit; a rejected value reverts.
        if (focused) if (str.accepts(input.text)) commit() else revert()
        return false
    }

    override fun mouseReleased(mouse: MouseButtonInput) =
        input.mouseReleased(mouse)

    override fun mouseDragged(drag: MouseDragInput) =
        input.mouseDragged(drag)

    override fun keyPressed(key: KeyInput) = input.keyPressed(key)

    override fun charTyped(typed: CharInput) = input.charTyped(typed)

    private fun commit() {
        if (!str.accepts(input.text)) {
            input.invalid = true
            return
        }
        str.value = input.text
        input.invalid = false
        input.focused = false
    }

    private fun revert() {
        input.invalid = false
        input.text = str.value
        input.focused = false
    }

    private companion object {
        const val ROW_INSET = 2f
    }
}
