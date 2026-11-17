package org.blackaddons.api.ui.widget.setting

import org.blackaddons.api.feature.settings.impl.ModeSetting
import org.blackaddons.api.feature.translatedOption
import org.blackaddons.api.ui.anim.Animation
import org.blackaddons.api.ui.anim.Easing
import org.blackaddons.api.ui.layout.centered
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.Tooltip
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.MouseButtonInput

class ModeWidget(setting: ModeSetting) : SettingWidget(setting) {

    private val mode = setting
    private var lastValue = mode.value
    private var previousValue: String? = null
    private val transitionAnim = Animation(1f, Theme.ANIM_NORMAL.toLong(), Easing.easeOutCubic)
    private var isNext = true

    private var measuredLabels: List<String>? = null
    private var widestLabel = 0f

    /**
     * Sized to the longest mode so it does not resize while cycling. The labels are measured again only when they
     * change.
     */
    override val naturalWidth: Float
        get() {
            val labels = mode.modes.map(mode::translatedOption)
            if (labels != measuredLabels) {
                measuredLabels = labels
                widestLabel = labels.maxOfOrNull { Render.textWidth(it, Theme.FONT_SMALL, Fonts.medium) } ?: 0f
            }
            return (widestLabel + ARROW_GUTTER * 2f).coerceIn(MIN_WIDTH, MAX_WIDTH)
        }

    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float) {
        val h = Theme.SETTING_ROW_HEIGHT - 4f
        val cy = y + 2f
        bounds.set(x, cy, widgetWidth, h)
        val hovered = bounds.has(mouseX, mouseY)
        val bg = if (hovered) Theme.SURFACE_HOVER else Theme.SURFACE
        Render.rect(x, cy, widgetWidth, h, bg, Theme.RADIUS_SMALL)

        val left = "<"
        val right = ">"
        val arrowSize = Theme.FONT_BODY
        val arrowY = Render.centerY(cy, h, arrowSize, Fonts.medium)
        Render.text(left, x + 8f, arrowY, arrowSize, Theme.TEXT_SECONDARY, Fonts.medium)
        Render.text(right, x + widgetWidth - 14f, arrowY, arrowSize, Theme.TEXT_SECONDARY, Fonts.medium)

        val value = mode.value
        if (value != lastValue) {
            previousValue = lastValue
            lastValue = value
            transitionAnim.set(0f)
            transitionAnim.animateTo(1f)
        }

        val t = transitionAnim.get()
        val full = mode.translatedOption(value)
        val label = Render.ellipsize(full, widgetWidth - ARROW_GUTTER * 2f, Theme.FONT_SMALL, Fonts.medium)
        if (hovered && label != full) Tooltip.show(full)

        val ty = Render.centerY(cy, h, Theme.FONT_SMALL, Fonts.medium)
        val textClipX = x + ARROW_GUTTER
        val textClipW = widgetWidth - ARROW_GUTTER * 2f
        Render.pushScissor(textClipX, cy, textClipW, h)

        if (t >= 1f) {
            val labelWidth = Render.textWidth(label, Theme.FONT_SMALL, Fonts.medium)
            Render.text(
                label,
                centered(x, widgetWidth, labelWidth),
                ty,
                Theme.FONT_SMALL, if (mode.isUnsafe) Theme.DANGER else Theme.TEXT_PRIMARY, Fonts.medium,
            )
        } else {
            val slideDist = 24f
            val direction = if (isNext) 1f else -1f

            if (previousValue != null) {
                val prevFull = mode.translatedOption(previousValue!!)
                val prevLabel = Render.ellipsize(prevFull, widgetWidth - ARROW_GUTTER * 2f, Theme.FONT_SMALL, Fonts.medium)
                val prevWidth = Render.textWidth(prevLabel, Theme.FONT_SMALL, Fonts.medium)
                val prevStartX = centered(x, widgetWidth, prevWidth)
                val prevXOffset = -t * slideDist * direction

                Render.text(
                    prevLabel,
                    prevStartX + prevXOffset,
                    ty,
                    Theme.FONT_SMALL,
                    Theme.TEXT_PRIMARY.withAlpha(1f - t),
                    Fonts.medium
                )
            }

            val labelWidth = Render.textWidth(label, Theme.FONT_SMALL, Fonts.medium)
            val startX = centered(x, widgetWidth, labelWidth)
            val currXOffset = (1f - t) * slideDist * direction

            Render.text(
                label,
                startX + currXOffset,
                ty,
                Theme.FONT_SMALL,
                Theme.TEXT_PRIMARY.withAlpha(t),
                Fonts.medium
            )
        }
        Render.popScissor()
    }

    private companion object {
        /** Arrow plus padding, per side. */
        const val ARROW_GUTTER = 20f
        const val MIN_WIDTH = 90f
    }

    override fun mouseClicked(mouse: MouseButtonInput): Boolean {
        if (!bounds.has(mouse.x, mouse.y)) return false
        when (mouse.button) {
            0 -> {
                if (mouse.x < bounds.x + bounds.w / 2f) {
                    isNext = false
                    mode.previous()
                } else {
                    isNext = true
                    mode.next()
                }
            }
            1 -> {
                isNext = false
                mode.previous()
            }
            else -> return false
        }
        return true
    }
}
