package org.blackaddons.core.ui.screen.fme

import org.blackaddons.api.Platform
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.ModalPanel
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.dropdown.Dropdown
import org.blackaddons.api.util.I18n
import org.blackaddons.api.world.BlockStateProperty
import org.blackaddons.core.ui.screen.fme.FmeBlockBrowser.Companion.displayName

/**
 * Pins state properties of [blockId] across [states]. Only rows the player changes reach [onApply], so properties that
 * differ between states stay as they are unless picked.
 */
internal class FmeBlockStateModal(
    blockId: String,
    states: List<Map<String, String>>,
    private val onApply: (edits: Map<String, String?>) -> Boolean,
) : ModalPanel(tr("block_state_title", "Block state: %s", displayName(blockId))) {

    private class Row(val property: BlockStateProperty, val dropdown: Dropdown, val initial: Int)

    private val rows = Platform.world.blockStateProperties(blockId).map { property ->
        val values = states.map { it[property.name] }.distinct()
        val mixed = values.size > 1
        val items = listOf(Dropdown.Item(tr("state_auto", "Auto"))) + property.values.map(Dropdown::Item) +
            listOfNotNull(Dropdown.Item(tr("state_mixed", "Mixed")).takeIf { mixed })
        val initial = if (mixed) items.lastIndex else values.singleOrNull()?.let { property.values.indexOf(it) + 1 } ?: 0
        val dropdown = widgets.add(Dropdown().apply {
            this.items = items
            selectedIndex = initial
        })
        Row(property, dropdown, initial)
    }
    private val applyButton = widgets.add(Button(tr("apply_position", "Apply")) { save() }.apply { accent = true })
    private val cancelButton = widgets.add(Button(tr("cancel", "Cancel")) { UiManager.pop() })

    override val panelWidth: Float get() = fitWidth(380f, min = 300f)
    override val panelHeight: Float get() = fitHeight(PANEL_BASE_H + rows.size * ROW_STEP, min = 150f)

    override fun renderContent(mouseX: Float, mouseY: Float) {
        Render.text(tr("state_hint", "Auto lets placement and the real block decide."), contentX, contentY, TextStyle.SMALL_MUTED)
        var y = contentY + HINT_H
        rows.forEach { row ->
            Render.text(row.property.name, contentX, Render.centerY(y, ROW_H, TextStyle.SMALL_SECONDARY_MEDIUM), TextStyle.SMALL_SECONDARY_MEDIUM)
            row.dropdown.render(contentX + LABEL_W, y, contentW - LABEL_W, ROW_H, mouseX, mouseY)
            y += ROW_STEP
        }
        val buttonY = contentY + contentH - ROW_H
        applyButton.render(contentX, buttonY, 72f, ROW_H, mouseX, mouseY)
        cancelButton.render(contentX + contentW - 76f, buttonY, 76f, ROW_H, mouseX, mouseY)
        rows.forEach { it.dropdown.renderOverlay(mouseX, mouseY) }
    }

    private fun save() {
        val edits = rows.filter { it.dropdown.selectedIndex != it.initial }.mapNotNull { row ->
            val index = row.dropdown.selectedIndex
            when {
                index == 0 -> row.property.name to null
                index <= row.property.values.size -> row.property.name to row.property.values[index - 1]
                else -> null
            }
        }.toMap()
        if (edits.isNotEmpty()) onApply(edits)
        UiManager.pop()
    }

    private companion object {
        val tr = I18n.scope("blackaddons.fme")

        const val PANEL_BASE_H = 124f
        const val HINT_H = 20f
        const val ROW_H = 22f
        const val ROW_STEP = 28f
        const val LABEL_W = 110f
    }
}
