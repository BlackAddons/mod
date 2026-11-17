package org.blackaddons.api.ui.widget.setting

import org.blackaddons.api.feature.settings.impl.ModeSetting
import org.blackaddons.api.feature.translatedOption
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.MouseDragInput
import org.blackaddons.api.ui.widget.MouseScrollInput
import org.blackaddons.api.ui.widget.dropdown.Dropdown

/** A [ModeSetting] marked `asDropdown()`, drawn as a [Dropdown]. */
class ModeDropdownWidget(setting: ModeSetting) : SettingWidget(setting) {

    private val mode = setting
    private val dropdown = Dropdown(maxVisibleItems = 8).apply { onSelect = { mode.value = mode.modes[it] } }
    private var labels: List<String>? = null
    private var itemsWidth = 0f

    override val naturalWidth: Float
        get() {
            synced()
            return itemsWidth.coerceIn(MIN_WIDTH, MAX_WIDTH)
        }

    override val isOverlaying: Boolean get() = dropdown.isOverlaying

    override fun render(x: Float, y: Float, mouseX: Float, mouseY: Float) {
        val h = Theme.SETTING_ROW_HEIGHT - 4f
        bounds.set(x, y + 2f, widgetWidth, h)
        synced().render(x, y + 2f, widgetWidth, h, mouseX, mouseY)
    }

    override fun renderOverlay(mouseX: Float, mouseY: Float) = dropdown.renderOverlay(mouseX, mouseY)

    override fun mouseClicked(mouse: MouseButtonInput): Boolean = dropdown.mouseClicked(mouse)

    override fun mouseDragged(drag: MouseDragInput): Boolean =
        dropdown.mouseDragged(drag)

    override fun mouseReleased(mouse: MouseButtonInput): Boolean = dropdown.mouseReleased(mouse)

    override fun mouseScrolled(wheel: MouseScrollInput): Boolean = dropdown.mouseScrolled(wheel)

    /*
     * Checked on every call, because some mode lists change at runtime and the value can change elsewhere. The labels
     * are measured again only when they change.
     */
    private fun synced(): Dropdown = dropdown.apply {
        val now = mode.modes.map(mode::translatedOption)
        if (now != labels) {
            labels = now
            items = now.map { Dropdown.Item(it) }
            itemsWidth = preferredWidth()
        }
        selectedIndex = mode.index.coerceAtLeast(0)
    }

    private companion object {
        const val MIN_WIDTH = 90f
    }
}
