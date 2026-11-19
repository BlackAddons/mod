package org.blackaddons.api.ui.screen

import org.blackaddons.api.feature.settings.impl.EntryOption
import org.blackaddons.api.feature.settings.impl.ListSetting
import org.blackaddons.api.feature.settings.impl.OrderSetting
import org.blackaddons.api.feature.translatedOption
import org.blackaddons.api.feature.translatedPlaceholder
import org.blackaddons.api.ui.layout.centered
import org.blackaddons.api.ui.layout.hit
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.ScrollableList
import org.blackaddons.api.ui.widget.UiMouse
import org.blackaddons.api.ui.widget.text.TextInput
import org.blackaddons.api.util.I18n

private val tr = I18n.scope("blackaddons.list")

/** An [OrderSetting] can only be reordered, with no input field and no delete. */
class ListEditorScreen(
    private val setting: ListSetting,
    title: String = setting.name,
) : ModalPanel(title) {

    private val list = widgets.add(ScrollableList<String>().apply {
        rowHeight = 22f
        emptyMessage = tr("empty", "No entries — add one below.")
        drawRow = { entry, index, rx, ry, rw -> renderRow(entry, index, rx, ry, rw) }
        onReorder = setting::move
    })
    private val ordered = setting as? OrderSetting
    private val field = if (ordered != null) null else widgets.add(TextInput(
        placeholder = setting.translatedPlaceholder.ifEmpty { tr("input_placeholder", "type new entry...") },
        maxLength = setting.itemMaxLength,
    ).apply { onEnter = { commitInput() } })
    private val addButton = if (ordered != null) null else widgets.add(Button("+ " + tr("add", "Add")) { commitInput() })
    private val doneButton = widgets.add(Button(tr("done", "Done")) { UiManager.pop() })

    override val panelWidth: Float get() = if (ordered == null) super.panelWidth else ORDER_W
    override val panelHeight: Float
        get() = ordered?.let { heightFor(orderListHeight(it.value.size)) } ?: super.panelHeight

    private fun orderListHeight(rows: Int): Float =
        rows.coerceIn(1, MAX_ORDER_ROWS) * (list.rowHeight + list.rowGap) + ScrollableList.EDGE_GAP * 2f

    // Set in renderContent, read in mouseClicked.
    private var inputY = 0f
    private var inputW = 0f

    override fun renderContent(mouseX: Float, mouseY: Float) {
        if (ordered != null) {
            list.items = setting.value
            list.render(contentX, contentY, contentW, footerTop - FOOTER_GAP - contentY)
            footer(mouseX, mouseY, doneButton)
            return
        }
        val doneX = contentX + contentW - DONE_W
        val doneY = contentY + contentH - DONE_H
        inputY = if (field == null) doneY else doneY - Theme.PADDING - INPUT_H
        inputW = contentW - ADD_W - Theme.PADDING_SMALL
        val listH = inputY - Theme.PADDING - contentY

        list.items = setting.value
        list.render(contentX, contentY, contentW, listH)

        field?.render(contentX, inputY, inputW, INPUT_H, mouseX, mouseY)
        addButton?.render(contentX + inputW + Theme.PADDING_SMALL, inputY, ADD_W, INPUT_H, mouseX, mouseY)
        doneButton.render(doneX, doneY, DONE_W, DONE_H, mouseX, mouseY)
    }

    private fun renderRow(entry: String, index: Int, rx: Float, ry: Float, rw: Float) {
        val mouseX = UiMouse.x
        val mouseY = UiMouse.y
        if (hit(mouseX, mouseY, rx, ry, rw, list.rowHeight)) {
            Render.rect(rx, ry, rw, list.rowHeight, Theme.SURFACE_HOVER.withAlpha(140), Theme.RADIUS_SMALL)
        }
        list.grip(index, rx, ry)
        val dx = rx + rw - DELETE_SIZE - 6f
        val dy = centered(ry, list.rowHeight, DELETE_SIZE)
        var chipsLeft = dx - CHIP_GAP
        if (ordered == null) {
            for (option in setting.entryOptions.asReversed()) chipsLeft = renderChoice(entry, option, chipsLeft, ry) - CHIP_GAP
        }
        val maxEntryW = (chipsLeft - 6f - rx - ScrollableList.GRIP_W).coerceAtLeast(20f)
        Render.text(
            ordered?.translatedOption(entry) ?: entry,
            rx + ScrollableList.GRIP_W,
            Render.centerY(ry, list.rowHeight, TextStyle.BODY_PRIMARY),
            TextStyle.BODY_PRIMARY, maxWidth = maxEntryW,
        )

        if (ordered != null) return
        val deleteHovered = widgets.clickable(dx, dy, DELETE_SIZE, DELETE_SIZE) {
            UiManager.confirmPopover(dx, dy, DELETE_SIZE, DELETE_SIZE) { setting.removeAt(index) }
        }
        val color = if (deleteHovered) Theme.DANGER else Theme.TEXT_MUTED
        Render.line(dx + 3f, dy + 3f, dx + DELETE_SIZE - 3f, dy + DELETE_SIZE - 3f, 1.6f, color)
        Render.line(dx + DELETE_SIZE - 3f, dy + 3f, dx + 3f, dy + DELETE_SIZE - 3f, 1.6f, color)
    }

    /** Drawn right-aligned to [right]; returns its left edge. A toggle is lit only while on. */
    private fun renderChoice(entry: String, option: EntryOption, right: Float, ry: Float): Float {
        val choice = setting.choiceOf(entry, option)
        val toggleLabel = option.label?.takeIf { option.choices.size == 2 }
        val toggle = toggleLabel != null
        val label = toggleLabel ?: setting.translatedOption(choice)
        val w = Render.textWidth(label, Theme.FONT_SMALL, Fonts.medium) + CHIP_PAD * 2f
        val x = right - w
        val y = centered(ry, list.rowHeight, CHIP_H)
        val hovered = widgets.clickable(x, y, w, CHIP_H) {
            if (option.choices.size == 2) {
                setting.setChoice(entry, option, option.choices.first { it != choice })
                return@clickable
            }
            UiManager.contextMenu(option.choices.map { c ->
                ContextMenu.Item(setting.translatedOption(c), checked = { setting.choiceOf(entry, option) == c }) {
                    setting.setChoice(entry, option, c)
                }
            })
        }
        val lit = !toggle || choice != option.default
        when {
            hovered -> Render.rect(x, y, w, CHIP_H, Theme.ACCENT, Theme.RADIUS_SMALL)
            lit -> Render.rect(x, y, w, CHIP_H, Theme.SURFACE_ACTIVE, Theme.RADIUS_SMALL)
        }
        Render.text(
            label, x + CHIP_PAD, Render.centerY(y, CHIP_H, Theme.FONT_SMALL, Fonts.medium), Theme.FONT_SMALL,
            if (hovered || lit) Theme.TEXT_PRIMARY else Theme.TEXT_MUTED, Fonts.medium,
        )
        if (hovered && toggle) Tooltip.show(setting.translatedOption(choice))
        return x
    }

    private fun commitInput() {
        val field = field ?: return
        val text = field.text.trim()
        if (text.isEmpty()) return
        setting.add(text)
        field.text = ""
    }

    private companion object {
        const val DELETE_SIZE = 14f
        const val CHIP_H = 16f
        const val CHIP_PAD = 6f
        const val CHIP_GAP = 8f
        const val INPUT_H = 22f
        const val ADD_W = 60f
        const val DONE_W = 80f
        const val DONE_H = 24f
        const val ORDER_W = 260f
        const val MAX_ORDER_ROWS = 8
    }
}
