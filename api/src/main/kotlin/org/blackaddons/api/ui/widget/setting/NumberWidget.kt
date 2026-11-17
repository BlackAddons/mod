package org.blackaddons.api.ui.widget.setting

import org.blackaddons.api.feature.settings.impl.NumberSetting
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.MouseDragInput
import org.blackaddons.api.ui.widget.MouseScrollInput
import org.blackaddons.api.ui.widget.Slider
import org.blackaddons.api.ui.widget.setting.SettingWidget.Companion.MAX_WIDTH
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.formatTrimmed
import org.blackaddons.api.util.near
import kotlin.math.abs
import kotlin.math.round

class NumberWidget(setting: NumberSetting) : SettingWidget(setting) {

    private val num = setting

    /**
     * Track plus value text. Not capped at [MAX_WIDTH] (that's for ellipsizable text); long units ask the
     * column for room instead of shrinking the track.
     */
    override val naturalWidth: Float
        get() = (MIN_TRACK_WIDTH + valueAreaW).coerceAtMost(MAX_TRACK_WIDTH)

    override val inputHint: String get() = I18n.trOr("blackaddons.widget.slider_hint", "Ctrl + scroll to adjust.")

    // The setting stays the source of truth, synced each frame.
    private val slider = Slider(num.min, num.max, num.value, num.step) { num.value = it }
        .apply { defaultValue = num.defaultValue }

    /**
     * From bounds, step and unit only (never the live value), so the track doesn't jitter. Measured once -
     * it's read many times per frame.
     */
    private val valueAreaW: Float by lazy { reservedValueWidth() + VALUE_GAP + VALUE_RIGHT_PAD }
    private val sliderW: Float get() = widgetWidth - valueAreaW

    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float) {
        slider.min = num.min
        slider.max = num.max
        slider.step = num.step
        // Silently, so drawing doesn't write back to the setting.
        slider.sync(num.value)
        slider.boxWidth = sliderW
        slider.render(x, y, sliderW, Theme.SETTING_ROW_HEIGHT, mouseX, mouseY)

        // Right-aligned so it grows leftward.
        val valueText = formatValue(num.value)
        val valueW = Render.textWidth(valueText, Theme.FONT_SMALL, Fonts.medium)
        Render.text(
            valueText,
            x + widgetWidth - VALUE_RIGHT_PAD - valueW,
            Render.centerY(y, Theme.SETTING_ROW_HEIGHT, Theme.FONT_SMALL, Fonts.medium),
            Theme.FONT_SMALL, if (num.isUnsafe) Theme.DANGER else Theme.TEXT_SECONDARY, Fonts.medium,
        )
    }

    private fun formatValue(value: Double): String {
        val rounded = if (num.step >= 1.0) round(value).toInt().toString() else value.formatTrimmed()
        return if (num.unit.isNotEmpty()) "$rounded ${num.unit}" else rounded
    }

    /** Widest displayable value; exact in the monospace font. */
    private fun reservedValueWidth(): Float {
        val intDigits = maxOf(abs(num.min), abs(num.max)).toLong().toString().length
        val places = decimals()
        val sample = buildString {
            if (num.min < 0.0) append('-')
            repeat(intDigits) { append('0') }
            if (places > 0) {
                append('.')
                repeat(places) { append('0') }
            }
            if (num.unit.isNotEmpty()) append(' ').append(num.unit)
        }
        return Render.textWidth(sample, Theme.FONT_SMALL, Fonts.medium)
    }

    /** What the step needs, capped at what [formatValue] prints. */
    private fun decimals(): Int {
        if (num.step >= 1.0) return 0
        var places = 0
        var scaled = num.step
        while (places < MAX_DECIMALS && !scaled.near(round(scaled))) {
            scaled *= 10.0
            places++
        }
        return places
    }

    override fun mouseClicked(mouse: MouseButtonInput): Boolean =
        slider.mouseClicked(mouse)

    override fun mouseReleased(mouse: MouseButtonInput): Boolean =
        slider.mouseReleased(mouse)

    override fun mouseDragged(drag: MouseDragInput): Boolean =
        slider.mouseDragged(drag)

    override fun mouseScrolled(wheel: MouseScrollInput): Boolean =
        slider.mouseScrolled(wheel)

    private companion object {
        const val VALUE_GAP = 8f
        const val VALUE_RIGHT_PAD = 2f
        const val MIN_TRACK_WIDTH = 140f

        /** Cap so one long unit can't stretch the column. */
        const val MAX_TRACK_WIDTH = 320f

        /** [formatValue] prints `%.2f`. */
        const val MAX_DECIMALS = 2
    }
}
