package org.blackaddons.extras.features.impl.extras.storage

import org.blackaddons.api.gui.ContainerMetrics
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.CharInput
import org.blackaddons.api.ui.widget.KeyInput
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.MouseDragInput
import org.blackaddons.api.ui.widget.text.TextInput

/** "Inventory" label and item search over the inventory. Owns query/focus, [onChange] lets the tile list scroll up. */
internal class StorageSearchBox(onChange: () -> Unit = {}) {

    private val input = TextInput(maxLength = StorageTheme.NAME_LIMIT).apply {
        onTextChanged = { onChange() }
        onEscape = { focused = false }
    }

    val text: String get() = input.text

    fun render(metrics: ContainerMetrics, mouseX: Int, mouseY: Int) {
        val y = metrics.inventoryTop - StorageTheme.SEARCH_H - 4
        val inventory = storageTr("inventory", "Inventory")
        val labelY = Render.centerY(y, StorageTheme.SEARCH_H, Theme.FONT_SMALL, Fonts.medium)
        Render.text(inventory, metrics.inventoryLeft, labelY, TextStyle.SMALL_SECONDARY_MEDIUM)
        // Fills the row right of the label.
        val x = metrics.inventoryLeft + Render.textWidth(inventory, Theme.FONT_SMALL, Fonts.medium) + 6f
        val w = (metrics.inventoryRight - x).coerceAtLeast(40f)
        input.placeholder = storageTr("search", "Search items...")
        input.render(x, y.toFloat(), w, StorageTheme.SEARCH_H.toFloat(), mouseX.toFloat(), mouseY.toFloat())
    }

    fun mouseClicked(mouse: MouseButtonInput): Boolean = input.mouseClicked(mouse)

    fun mouseDragged(drag: MouseDragInput): Boolean = input.mouseDragged(drag)

    fun mouseReleased(mouse: MouseButtonInput) {
        input.mouseReleased(mouse)
    }

    /** Eats every key while focused, so vanilla hotkeys don't fire. */
    fun keyPressed(key: KeyInput): Boolean {
        if (!input.focused) return false
        input.keyPressed(key)
        return true
    }

    fun charTyped(codepoint: Int): Boolean {
        if (!input.focused) return false
        Character.toChars(codepoint).forEach { input.charTyped(CharInput(it, 0)) }
        return true
    }

    fun reset() = input.clear()
}
