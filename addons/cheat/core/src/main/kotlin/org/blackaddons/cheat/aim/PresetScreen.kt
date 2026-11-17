package org.blackaddons.cheat.aim

import org.blackaddons.api.input.Keys
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.ContextMenu
import org.blackaddons.api.ui.screen.ModalPanel
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.ScrollableList
import org.blackaddons.api.ui.widget.Slider
import org.blackaddons.api.ui.widget.text.TextInput
import org.blackaddons.api.util.I18n
import org.blackaddons.cheat.features.impl.settings.Rotations

private val tr = I18n.scope("blackaddons.rotation_presets")

/* Preset cards on the left, values on the right. Built-ins are read-only (duplicate to edit), custom ones edit live. */
internal class PresetScreen : ModalPanel(tr("title", "Presets")) {

    override val panelWidth: Float = 560f
    override val panelHeight: Float = 340f

    private var selectedName = AimPreset.DEFAULT.name
    private var syncing = false

    private val selected: AimPreset get() = Rotations.preset(selectedName) ?: AimPreset.DEFAULT
    private val editable: Boolean get() = !Rotations.isBuiltIn(selected)

    private val cards = widgets.add(ScrollableList<AimPreset>().apply {
        rowHeight = CARD_H
        rowGap = Theme.PADDING_SMALL
        drawBackground = false
        drawRow = { preset, _, x, y, w -> drawCard(preset, x, y, w) }
    })

    private val nameField = widgets.add(TextInput(placeholder = tr("name_placeholder", "Preset name"), maxLength = Rotations.NAME_MAX).apply {
        onTextChanged = { rename(it) }
    })

    private val sliders = PresetField.entries.associateWith { field ->
        widgets.add(Slider(field.min.toDouble(), field.max.toDouble(), step = field.step.toDouble()) { value ->
            if (!syncing && editable) Rotations.update(selectedName, field.set(selected, value.toFloat()))
        })
    }

    private val newButton = widgets.add(Button(tr("new", "New Preset")) { duplicate(selected) })
    private val doneButton = widgets.add(Button(tr("done", "Done")) { UiManager.pop() })

    init {
        select(selected)
    }

    override fun renderContent(mouseX: Float, mouseY: Float) {
        val bodyH = footerTop - ModalPanel.FOOTER_GAP - contentY
        cards.items = Rotations.presets
        cards.render(contentX, contentY, LIST_W, bodyH)
        drawEditor(contentX + LIST_W + Theme.PADDING_LARGE, contentY, contentW - LIST_W - Theme.PADDING_LARGE, mouseX, mouseY)
        footer(mouseX, mouseY, doneButton, leading = newButton)
    }

    private fun drawCard(preset: AimPreset, x: Float, y: Float, w: Float) {
        val hovered = widgets.clickable(x, y, w, CARD_H) { select(preset) }
        widgets.clickable(x, y, w, CARD_H, Keys.MOUSE_RIGHT) {
            select(preset)
            UiManager.contextMenu(menu(preset))
        }
        val chosen = preset.name == selectedName
        val fill = when {
            chosen -> Theme.SURFACE_ACTIVE
            hovered -> Theme.SURFACE_HOVER
            else -> Theme.SURFACE
        }
        Render.rect(x, y, w, CARD_H, fill, Theme.RADIUS_SMALL)
        if (chosen) Render.hollowRect(x, y, w, CARD_H, 1f, Theme.ACCENT, Theme.RADIUS_SMALL)

        val left = x + Theme.PADDING
        val tag = if (Rotations.isBuiltIn(preset)) tr("built_in", "Built in") else ""
        val tagW = if (tag.isEmpty()) 0f else Render.textWidth(tag, Theme.FONT_SMALL, Fonts.regular) + Theme.PADDING
        Render.text(
            preset.name, left, Render.centerY(y, CARD_H, TextStyle.BODY_PRIMARY_MEDIUM), TextStyle.BODY_PRIMARY_MEDIUM,
            maxWidth = w - Theme.PADDING * 2f - tagW,
        )
        if (tag.isNotEmpty()) Render.text(tag, x + w - tagW, Render.centerY(y, CARD_H, TextStyle.SMALL_MUTED), TextStyle.SMALL_MUTED)
    }

    private fun drawEditor(x: Float, y: Float, w: Float, mouseX: Float, mouseY: Float) {
        var rowY = y + ROW_H + Theme.PADDING
        if (editable) {
            nameField.render(x, y, w, ROW_H - 2f, mouseX, mouseY)
        } else {
            Render.text(selected.name, x, y + 4f, TextStyle.BODY_PRIMARY_MEDIUM)
            val note = tr("note_built_in", "Built in and read-only. Duplicate it to make your own.")
            Render.wrappedText(note, x, y + NOTE_Y, w, TextStyle.SMALL_MUTED)
            rowY = maxOf(rowY, y + NOTE_Y + Render.wrappedTextHeight(note, w, Theme.FONT_SMALL, Fonts.regular) + Theme.PADDING)
        }
        for ((field, slider) in sliders) {
            slider.enabled = editable
            val label = tr("field.${field.key}", field.label)
            Render.text(label, x, Render.centerY(rowY, ROW_H, TextStyle.SMALL_SECONDARY), TextStyle.SMALL_SECONDARY)
            slider.render(x + LABEL_W, rowY, w - LABEL_W - VALUE_W, ROW_H, mouseX, mouseY)
            val value = show(field, selected)
            val valueW = Render.textWidth(value, Theme.FONT_SMALL, Fonts.medium)
            Render.text(value, x + w - valueW, Render.centerY(rowY, ROW_H, TextStyle.SMALL_PRIMARY_MEDIUM), TextStyle.SMALL_PRIMARY_MEDIUM)
            rowY += ROW_H
        }
    }

    private fun menu(preset: AimPreset): List<ContextMenu.Entry> {
        val custom = !Rotations.isBuiltIn(preset)
        return listOf(
            ContextMenu.Item(tr("duplicate", "Duplicate")) { duplicate(preset) },
            ContextMenu.Item(tr("rename", "Rename"), enabled = custom) { widgets.focus(nameField) },
            ContextMenu.Separator,
            ContextMenu.Item(
                tr("delete", "Delete"),
                danger = true,
                enabled = custom,
                confirm = tr("delete_confirm", "Delete %s?", preset.name),
            ) {
                Rotations.delete(preset.name)
                select(AimPreset.DEFAULT)
            },
        )
    }

    private fun select(preset: AimPreset) {
        selectedName = preset.name
        widgets.focus(null)
        syncing = true
        nameField.text = preset.name
        nameField.invalid = false
        for ((field, slider) in sliders) {
            slider.value = field.get(preset).toDouble()
            slider.defaultValue = field.get(AimPreset.DEFAULT).toDouble()
        }
        syncing = false
    }

    private fun duplicate(preset: AimPreset) {
        val copy = preset.copy(name = Rotations.freeName(preset.name))
        Rotations.add(copy)
        select(copy)
    }

    private fun rename(text: String) {
        if (syncing || !editable) return
        val name = text.trim()
        nameField.invalid = !Rotations.isFreeName(name, except = selectedName)
        if (nameField.invalid || name == selectedName) return
        Rotations.update(selectedName, selected.copy(name = name))
        selectedName = name
    }

    private fun show(field: PresetField, preset: AimPreset): String = field.display(field.get(preset))

    private companion object {
        const val LIST_W = 200f
        const val CARD_H = 26f
        const val NOTE_Y = 18f
        const val ROW_H = 24f
        const val LABEL_W = 110f
        const val VALUE_W = 52f
    }
}
