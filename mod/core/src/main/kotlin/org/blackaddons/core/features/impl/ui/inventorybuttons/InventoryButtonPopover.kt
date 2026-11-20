package org.blackaddons.core.features.impl.ui.inventorybuttons

import org.blackaddons.api.ui.icon.LucideIcons
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.Popover
import org.blackaddons.api.ui.screen.Tooltip
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.dropdown.Dropdown
import org.blackaddons.api.ui.widget.text.TextInput
import org.blackaddons.api.util.I18n
import org.blackaddons.core.actions.ActionStorage

private val tr = I18n.scope("blackaddons.inventory_buttons.editor")

// Changes apply live and closing saves. The anchor is in panel units.
internal class InventoryButtonPopover(
    private val button: InventoryButton,
    private val anchorX: Float,
    private val anchorY: Float,
    private val anchorW: Float,
    private val anchorH: Float,
) : Popover() {

    private val shortcuts = ActionStorage.shortcuts.toList()

    private val label = widgets.add(TextInput(placeholder = tr("label_hint", "Optional"))).apply {
        text = button.label
        onTextChanged = { button.label = it }
    }

    private val icon = widgets.add(TextInput(placeholder = tr("icon_hint", "HYPERION, skull:<hash>, diamond or /give ..."))).apply {
        text = button.icon
        onTextChanged = { button.icon = it }
    }

    private val runs = widgets.add(Dropdown()).apply {
        items = listOf(Dropdown.Item(tr("runs.command", "Command")), Dropdown.Item(tr("runs.shortcut", "Shortcut action")))
        selectedIndex = button.runs.ordinal
        onSelect = { index ->
            button.runs = ButtonRuns.entries[index]
            if (button.runs == ButtonRuns.SHORTCUT && shortcuts.none { it.id.toString() == button.shortcutId }) {
                shortcuts.firstOrNull()?.let { button.shortcutId = it.id.toString() }
            }
        }
    }

    private val command = widgets.add(TextInput(placeholder = tr("command_hint", "warp hub"), prefix = "/")).apply {
        text = button.command
        onTextChanged = { button.command = it.removePrefix("/") }
    }

    private val shortcut = widgets.add(Dropdown()).apply {
        items = if (shortcuts.isEmpty()) {
            listOf(Dropdown.Item(tr("no_shortcuts", "No shortcuts yet")))
        } else {
            shortcuts.map { Dropdown.Item(it.name.ifBlank { it.alias.ifBlank { tr("unnamed", "Unnamed") } }) }
        }
        selectedIndex = shortcuts.indexOfFirst { it.id.toString() == button.shortcutId }.coerceAtLeast(0)
        onSelect = { index -> shortcuts.getOrNull(index)?.let { button.shortcutId = it.id.toString() } }
    }

    private val scope = widgets.add(Dropdown()).apply {
        items = listOf(
            Dropdown.Item(tr("scope.inventory", "Inventory")),
            Dropdown.Item(tr("scope.all", "All containers")),
            Dropdown.Item(tr("scope.title", "Title contains")),
        )
        selectedIndex = button.scope.ordinal
        onSelect = { index -> button.scope = ButtonScope.entries[index] }
    }

    private val title = widgets.add(TextInput(placeholder = tr("title_hint", "Part of the container title"))).apply {
        text = button.titleFilter
        onTextChanged = { button.titleFilter = it }
    }

    private val done = widgets.add(Button(tr("done", "Done")) { UiManager.pop() }.apply { accent = true })

    override fun render(mouseX: Float, mouseY: Float, deltaTicks: Float) {
        val rows = buildList {
            add(Row(tr("caption.label", "Label")) { x, y, w -> label.render(x, y, w, FIELD_H, mouseX, mouseY) })
            add(Row(tr("caption.icon", "Icon")) { x, y, w -> iconRow(x, y, w, mouseX, mouseY) })
            add(Row(tr("caption.runs", "Runs")) { x, y, w -> runs.render(x, y, w, FIELD_H, mouseX, mouseY) })
            if (button.runs == ButtonRuns.COMMAND) {
                add(Row(tr("caption.command", "Command")) { x, y, w -> command.render(x, y, w, FIELD_H, mouseX, mouseY) })
            } else {
                add(Row(tr("caption.shortcut", "Shortcut")) { x, y, w -> shortcut.render(x, y, w, FIELD_H, mouseX, mouseY) })
            }
            add(Row(tr("caption.scope", "Show in")) { x, y, w -> scope.render(x, y, w, FIELD_H, mouseX, mouseY) })
            if (button.scope == ButtonScope.TITLE) add(Row(null) { x, y, w -> title.render(x, y, w, FIELD_H, mouseX, mouseY) })
        }
        val captionH = Render.textHeight(TextStyle.SMALL_MUTED_MEDIUM.size, TextStyle.SMALL_MUTED_MEDIUM.font)
        val rowsH = rows.sumOf { ((if (it.caption != null) captionH + CAPTION_GAP else 0f) + FIELD_H + ROW_GAP).toDouble() }.toFloat()
        val h = Theme.PADDING * 2f + rowsH + BUTTON_H
        placeUnder(anchorX, anchorY, anchorW, anchorH, WIDTH, h, GAP)

        drawCard(rise = RISE) {
            val x = card.x + Theme.PADDING
            val w = WIDTH - Theme.PADDING * 2f
            var y = card.y + Theme.PADDING
            for (row in rows) {
                row.caption?.let {
                    Render.text(it, x, y, TextStyle.SMALL_MUTED_MEDIUM)
                    y += captionH + CAPTION_GAP
                }
                row.draw(x, y, w)
                y += FIELD_H + ROW_GAP
            }
            done.render(x, y, w, BUTTON_H, mouseX, mouseY)
        }
    }

    private fun iconRow(x: Float, y: Float, w: Float, mouseX: Float, mouseY: Float) {
        icon.render(x, y, w - FIELD_H - Theme.PADDING_SMALL, FIELD_H, mouseX, mouseY)
        val px = x + w - FIELD_H
        val hovered = widgets.clickable(px, y, FIELD_H, FIELD_H) {
            UiManager.push(IconPicker(card.x, card.x + card.w, y, button.icon) { icon.text = it })
        }
        Render.rect(px, y, FIELD_H, FIELD_H, if (hovered) Theme.SURFACE_HOVER else Theme.SURFACE, Theme.RADIUS_SMALL)
        Render.hollowRect(px, y, FIELD_H, FIELD_H, 1f, if (hovered) Theme.TEXT_MUTED else Theme.BORDER, Theme.RADIUS_SMALL)
        val spec = ButtonIcons.spec(button.icon)
        if (spec != null) {
            val inset = (FIELD_H - PREVIEW) / 2f
            Render.itemSpec(spec, px + inset, y + inset, PREVIEW, PREVIEW)
        } else {
            val inset = (FIELD_H - EMPTY_ICON) / 2f
            Render.icon(LucideIcons.image, px + inset, y + inset, EMPTY_ICON, if (hovered) Theme.TEXT_PRIMARY else Theme.TEXT_MUTED)
        }
        if (hovered) Tooltip.show(tr("pick_icon", "Pick an item"))
    }

    override fun renderOverlay(mouseX: Float, mouseY: Float) {
        runs.renderOverlay(mouseX, mouseY)
        shortcut.renderOverlay(mouseX, mouseY)
        scope.renderOverlay(mouseX, mouseY)
    }

    override fun onClose() {
        InventoryButtonStore.save()
    }

    private class Row(val caption: String?, val draw: (Float, Float, Float) -> Unit)

    private companion object {
        const val WIDTH = 200f
        const val FIELD_H = 20f
        const val BUTTON_H = 20f
        const val CAPTION_GAP = 2f
        const val ROW_GAP = 6f
        const val PREVIEW = 16f
        const val EMPTY_ICON = 12f
        const val GAP = 4f
        const val RISE = 4f
    }
}
