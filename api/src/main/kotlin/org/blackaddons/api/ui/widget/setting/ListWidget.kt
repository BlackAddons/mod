package org.blackaddons.api.ui.widget.setting

import org.blackaddons.api.feature.settings.impl.ListSetting
import org.blackaddons.api.feature.settings.impl.OrderSetting
import org.blackaddons.api.feature.translatedOption
import org.blackaddons.api.ui.layout.centered
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.ListEditorScreen
import org.blackaddons.api.ui.screen.Tooltip
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.util.I18n

/** Shows "N items" (an order setting lists its entries instead) and an Edit button that opens [ListEditorScreen]. */
class ListWidget(setting: ListSetting) : SettingWidget(setting) {

    private val list = setting
    override val naturalWidth: Float
        get() = (Render.textWidth(summary(), Theme.FONT_SMALL, Fonts.medium) + CONTENT_PAD * 2f)
            .coerceIn(MIN_WIDTH, MAX_WIDTH)

    private fun summary(): String {
        val edit = I18n.trOr("blackaddons.list.edit", "Edit")
        if (list is OrderSetting) return list.value.joinToString(", ") { list.translatedOption(it) } + " · $edit"
        val n = list.value.size
        val label = I18n.trOr(
            "blackaddons.list.summary",
            "$n " + if (n == 1) "item" else "items",
            n,
        )
        return "$label · $edit"
    }

    private companion object {
        const val MIN_WIDTH = 110f
    }

    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float) {
        val h = Theme.SETTING_ROW_HEIGHT - 4f
        val cy = y + 2f
        bounds.set(x, cy, widgetWidth, h)
        val hovered = bounds.has(mouseX, mouseY)
        val bg = if (hovered) Theme.ACCENT else Theme.SURFACE_ACTIVE
        Render.rect(x, cy, widgetWidth, h, bg, Theme.RADIUS_SMALL)

        val full = summary()
        val combined = Render.ellipsize(full, widgetWidth - CONTENT_PAD * 2f, Theme.FONT_SMALL, Fonts.medium)
        if (hovered && combined != full) Tooltip.show(full)
        val lw = Render.textWidth(combined, Theme.FONT_SMALL, Fonts.medium)
        Render.text(
            combined,
            centered(x, widgetWidth, lw),
            Render.centerY(cy, h, Theme.FONT_SMALL, Fonts.medium),
            Theme.FONT_SMALL,
            if (hovered) Theme.TEXT_PRIMARY else Theme.TEXT_SECONDARY,
            Fonts.medium,
        )
    }

    override fun mouseClicked(mouse: MouseButtonInput): Boolean {
        if (!mouse.isLeft || !bounds.has(mouse.x, mouse.y)) return false
        UiManager.push(ListEditorScreen(setting = list))
        return true
    }
}
