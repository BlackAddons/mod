package org.blackaddons.api.ui.node

import org.blackaddons.api.ui.layout.Align
import org.blackaddons.api.ui.layout.LayoutBuilder
import org.blackaddons.api.ui.layout.columnWidth
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.screen.color.ColorPopover
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.InputTarget
import org.blackaddons.api.ui.widget.MovementKeybindSelector
import org.blackaddons.api.ui.widget.WidgetHost
import org.blackaddons.api.ui.widget.dropdown.Dropdown
import org.blackaddons.api.ui.widget.dropdown.MultiSelectDropdown
import org.blackaddons.api.ui.widget.text.TextInput
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.formatTrimmed
import java.awt.Color

/**
 * [NodeProperty] rows as a modal form, with each label above its control. Edits write straight through the accessors.
 * Any change other than typing can reshape the rows, so they are read again before the next frame and [onReshape] runs
 * for the host to register the new widgets. [NodeProperty.Binding] rows are read-only.
 */
class NodePropertyForm(private val read: () -> List<NodeProperty>, private val onReshape: () -> Unit) {

    private var fields: List<Field> = emptyList()
    private var stale = true

    fun attachTo(host: WidgetHost) {
        refresh()
        fields.forEach { field -> field.widgets.forEach { host.add(it) } }
    }

    fun section(builder: LayoutBuilder, width: Float, mouseX: Float, mouseY: Float) {
        if (stale && fields.none { it.isOpen }) {
            refresh()
            onReshape()
        }
        fields.forEach { it.layout(builder, width, mouseX, mouseY) }
    }

    fun renderOverlays(mouseX: Float, mouseY: Float) = fields.forEach { it.overlay(mouseX, mouseY) }

    private fun refresh() {
        if (!stale) return
        stale = false
        fields = read().map(::fieldOf)
    }

    /** Reads the rows again before the next frame, for example after the host changed what they edit. */
    fun invalidate() {
        stale = true
    }

    private fun fieldOf(property: NodeProperty): Field = when (property) {
        is NodeProperty.Number -> NumberField(property)
        is NodeProperty.Toggle -> ToggleField(property)
        is NodeProperty.Choice -> ChoiceField(property)
        is NodeProperty.MovementKeys -> MovementKeysField(property)
        is NodeProperty.Flags -> FlagsField(property)
        is NodeProperty.Text -> TextField(property)
        is NodeProperty.Swatch -> SwatchField(property)
        is NodeProperty.Binding -> NoteField(property.label, property.describe(property.get()))
        is NodeProperty.Buttons -> ButtonsField(property)
        is NodeProperty.Note -> NoteField("", property.text)
    }

    private abstract class Field {
        open val widgets: List<InputTarget> get() = emptyList()
        open val isOpen: Boolean get() = false

        abstract fun layout(builder: LayoutBuilder, width: Float, mouseX: Float, mouseY: Float)

        open fun overlay(mouseX: Float, mouseY: Float) {}

        protected fun LayoutBuilder.label(text: String) {
            if (text.isNotEmpty()) text(text, TextStyle.SMALL_MUTED_MEDIUM)
        }
    }

    private class NumberField(private val property: NodeProperty.Number) : Field() {
        private val input = TextInput().apply {
            text = property.get().formatTrimmed(property.decimals)
            onTextChanged = { typed ->
                val value = typed.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
                if (value == null) invalid = typed.isNotBlank() else property.set(value.coerceIn(property.min, property.max))
            }
        }
        override val widgets: List<InputTarget> = listOf(input)

        override fun layout(builder: LayoutBuilder, width: Float, mouseX: Float, mouseY: Float) = with(builder) {
            label(property.label + (property.labelSuffix?.invoke(property.get()) ?: ""))
            cell(width, ROW_H) { x, y -> input.render(x, y, width, ROW_H, mouseX, mouseY) }
        }
    }

    private inner class ToggleField(private val property: NodeProperty.Toggle) : Field() {
        private val button = Button(toggleLabel()) {
            property.set(!property.get())
            invalidate()
        }
        override val widgets: List<InputTarget> = listOf(button)

        private fun toggleLabel(): String {
            val on = property.get()
            return "${property.label}: ${I18n.trOr(if (on) "blackaddons.actions.on" else "blackaddons.actions.off", if (on) "ON" else "OFF")}"
        }

        override fun layout(builder: LayoutBuilder, width: Float, mouseX: Float, mouseY: Float) = with(builder) {
            button.label = toggleLabel()
            cell(width, ROW_H) { x, y -> button.render(x, y, width, ROW_H, mouseX, mouseY) }
        }
    }

    private inner class ChoiceField(private val property: NodeProperty.Choice) : Field() {
        private val dropdown = Dropdown().apply {
            items = property.options.map { Dropdown.Item(it) }
            selectedIndex = property.get().coerceIn(0, (property.options.size - 1).coerceAtLeast(0))
            onSelect = { index ->
                property.set(index)
                invalidate()
            }
        }
        override val widgets: List<InputTarget> = listOf(dropdown)
        override val isOpen: Boolean get() = dropdown.open

        override fun layout(builder: LayoutBuilder, width: Float, mouseX: Float, mouseY: Float) = with(builder) {
            label(property.label)
            cell(width, ROW_H) { x, y -> dropdown.render(x, y, width, ROW_H, mouseX, mouseY) }
        }

        override fun overlay(mouseX: Float, mouseY: Float) = dropdown.renderOverlay(mouseX, mouseY)
    }

    private inner class FlagsField(private val property: NodeProperty.Flags) : Field() {
        private val dropdown = MultiSelectDropdown(placeholder = property.none).apply {
            items = property.options.map { MultiSelectDropdown.Item(it, property.labelOf(it)) }
            selectedIds += property.get()
            onToggle = { _, _ ->
                property.set(selectedIds.toSet())
                invalidate()
            }
        }
        override val widgets: List<InputTarget> = listOf(dropdown)
        override val isOpen: Boolean get() = dropdown.open

        override fun layout(builder: LayoutBuilder, width: Float, mouseX: Float, mouseY: Float) = with(builder) {
            label(property.label)
            cell(width, ROW_H) { x, y -> dropdown.render(x, y, width, ROW_H, mouseX, mouseY) }
        }

        override fun overlay(mouseX: Float, mouseY: Float) = dropdown.renderOverlay(mouseX, mouseY)
    }

    private class MovementKeysField(private val property: NodeProperty.MovementKeys) : Field() {
        private val selector = MovementKeybindSelector(property.keys()) { property.setKeys(it) }
        override val widgets: List<InputTarget> = listOf(selector)

        override fun layout(builder: LayoutBuilder, width: Float, mouseX: Float, mouseY: Float) = with(builder) {
            label(property.label)
            cell(width, selector.height) { x, y -> selector.render(x, y, width, mouseX, mouseY) }
        }
    }

    private class TextField(private val property: NodeProperty.Text) : Field() {
        private val input = TextInput(placeholder = property.placeholder).apply {
            text = property.get()
            suggestions = property.suggestions
            suggestionDisplay = property.suggestionDisplay
            onTextChanged = { property.set(it) }
        }
        override val widgets: List<InputTarget> = listOf(input)
        override val isOpen: Boolean get() = input.isOverlaying

        override fun layout(builder: LayoutBuilder, width: Float, mouseX: Float, mouseY: Float) = with(builder) {
            label(property.label)
            cell(width, ROW_H) { x, y -> input.render(x, y, width, ROW_H, mouseX, mouseY) }
        }

        override fun overlay(mouseX: Float, mouseY: Float) = input.renderSuggestions(mouseX, mouseY)
    }

    private class SwatchField(private val property: NodeProperty.Swatch) : Field() {
        private val button = Button(I18n.trOr("blackaddons.actions.waypoint.pick_color", "Pick Color")) {
            UiManager.push(ColorPopover(initial = opaque(), allowAlpha = false, onChange = { property.set(it.rgb and RGB) }))
        }
        override val widgets: List<InputTarget> = listOf(button)

        private fun opaque() = Color(property.get() or OPAQUE, true)

        override fun layout(builder: LayoutBuilder, width: Float, mouseX: Float, mouseY: Float) = with(builder) {
            label(property.label)
            row(spacing = Theme.PADDING_SMALL, align = Align.CENTER, width = width) {
                cell(SWATCH_W, ROW_H) { x, y ->
                    Render.rect(x, y, SWATCH_W, ROW_H, opaque(), Theme.RADIUS_SMALL)
                    Render.hollowRect(x, y, SWATCH_W, ROW_H, 1f, Theme.BORDER, Theme.RADIUS_SMALL)
                }
                val buttonW = width - SWATCH_W - Theme.PADDING_SMALL
                cell(buttonW, ROW_H) { x, y -> button.render(x, y, buttonW, ROW_H, mouseX, mouseY) }
            }
        }
    }

    private inner class ButtonsField(property: NodeProperty.Buttons) : Field() {
        private val buttons = property.buttons.map { (label, action) ->
            Button(label) {
                action()
                invalidate()
            }
        }
        override val widgets: List<InputTarget> = buttons

        override fun layout(builder: LayoutBuilder, width: Float, mouseX: Float, mouseY: Float) = with(builder) {
            if (buttons.isEmpty()) return@with
            row(spacing = Theme.PADDING_SMALL, align = Align.CENTER, width = width) {
                val each = columnWidth(width, buttons.size, Theme.PADDING_SMALL)
                buttons.forEach { button -> cell(each, ROW_H) { x, y -> button.render(x, y, each, ROW_H, mouseX, mouseY) } }
            }
        }
    }

    private class NoteField(private val title: String, private val text: String) : Field() {
        override fun layout(builder: LayoutBuilder, width: Float, mouseX: Float, mouseY: Float) = with(builder) {
            label(title)
            text(text, TextStyle.SMALL_MUTED)
        }
    }

    private companion object {
        const val ROW_H = 22f
        const val SWATCH_W = 22f
        const val RGB = 0xFFFFFF
        const val OPAQUE = 0xFF000000.toInt()
    }
}
