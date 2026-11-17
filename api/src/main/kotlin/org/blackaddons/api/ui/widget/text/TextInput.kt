package org.blackaddons.api.ui.widget.text

import org.blackaddons.api.Platform
import org.blackaddons.api.input.Keys
import org.blackaddons.api.ui.icon.LucideIcons
import org.blackaddons.api.ui.layout.Rect
import org.blackaddons.api.ui.layout.centered
import org.blackaddons.api.ui.render.Font
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.ui.widget.CharInput
import org.blackaddons.api.ui.widget.KeyInput
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.MouseDragInput
import org.blackaddons.api.ui.widget.MouseScrollInput
import org.blackaddons.api.ui.widget.UiMouse
import org.blackaddons.api.ui.widget.Widget
import org.blackaddons.api.util.graphemes

/**
 * The toolkit's text field: a [TextEditor] in a box with focus, [placeholder] and a fixed [prefix] (not part
 * of [text]). Enter/Escape run [onEnter]/[onEscape] (blur by default).
 */
class TextInput(
    var placeholder: String = "",
    var prefix: String = "",
    maxLength: Int = Int.MAX_VALUE,
    charFilter: (Char) -> Boolean = { true },
    charTransformer: (Char) -> Char = { it },
) : Widget {

    val editor = TextEditor(maxLength, charFilter, charTransformer)

    private val suggestionList = SuggestionList()

    /** Null = no autocomplete. */
    var suggestions: ((String) -> List<String>)?
        get() = suggestionList.supplier
        set(value) { suggestionList.supplier = value }

    /** Display only; the full value is inserted. */
    var suggestionDisplay: ((String) -> String)?
        get() = suggestionList.display
        set(value) { suggestionList.display = value }

    var text: String
        get() = editor.text
        set(value) {
            editor.text = value
            suggestionList.undismiss()
            reportChange()
        }

    /** Cleared on the next successful edit. */
    var invalid: Boolean = false

    /** Shown instead of [text] while unfocused. */
    var displayText: (() -> String)? = null

    /** Eye icon to show/hide masked text. */
    var hasEyeToggle: Boolean = false

    var isMasked: Boolean = false
        set(value) {
            field = value
            editor.maskChar = if (value) maskChar else null
        }

    var maskChar: Char? = '•'
        set(value) {
            field = value
            editor.maskChar = if (isMasked) value else null
        }

    var fontSize: Float = Theme.FONT_SMALL
    var font: Font = Fonts.regular

    /** Off when the host commits on outside click. */
    var blurOnClickOutside: Boolean = true

    var onEnter: () -> Unit = { focused = false }
    var onEscape: () -> Unit = { if (text.isNotEmpty()) text = "" else focused = false }
    var onFocusGained: () -> Unit = {}

    override val isOverlaying: Boolean get() = suggestionList.isOpen

    /** Fires on any change: typed, pasted, completed, cleared or assigned. */
    var onTextChanged: (String) -> Unit = {}
    private var reportedText = ""

    var suggestionMinWidth: Float = 0f

    var boxWidth: Float = 120f
    var boxHeight: Float = 22f
    override val width: Float get() = boxWidth
    override val height: Float get() = boxHeight

    override var focused: Boolean = false
        set(value) {
            if (value == field) return
            field = value
            Platform.screenHost.textInputFocusChanged(this, value)
            if (value) {
                suggestionList.undismiss()
                editor.snap()
                onFocusGained()
            } else {
                suggestionList.close()
            }
        }

    private val bounds = Rect()
    private var dragging = false

    override fun draw(x: Float, y: Float) = render(x, y, width, height, UiMouse.x, UiMouse.y)

    fun render(x: Float, y: Float, w: Float, h: Float, mouseX: Float, mouseY: Float) {
        bounds.set(x, y, w, h)

        val border = when {
            invalid -> Theme.DANGER
            focused -> Theme.ACCENT
            else -> Theme.BORDER
        }
        Render.rect(x, y, w, h, Theme.SURFACE.withAlpha(80), Theme.RADIUS_SMALL)
        Render.rect(x, y + h - 1f, w, 1f, border)

        val ty = Render.centerY(y, h, fontSize, font)
        var textX = x + Theme.PADDING
        if (prefix.isNotEmpty()) {
            Render.text(prefix, textX, ty, fontSize, Theme.TEXT_PRIMARY, font)
            textX += Render.textWidth(prefix, fontSize, font)
        }

        val eyeW = if (hasEyeToggle) 20f else 0f
        val limitW = maxOf(0f, x + w - Theme.PADDING - textX - eyeW)
        editor.updateLayout(limitW, fontSize, font)

        Render.pushScissor(textX, y, limitW, h)
        if (focused) {
            editor.draw(
                x = textX, ty = ty, fontSize = fontSize, font = font,
                textColor = Theme.TEXT_PRIMARY, accentColor = Theme.ACCENT.withAlpha(90),
            )
        } else {
            val raw = displayText?.invoke() ?: text
            val shown = if (isMasked && maskChar != null) maskChar.toString().repeat(raw.graphemes().size) else raw
            when {
                shown.isEmpty() -> Render.text(placeholder, textX, ty, fontSize, Theme.TEXT_MUTED, font)
                // Picture cells look the same unfocused.
                displayText == null && !isMasked && editor.appearance != null -> editor.draw(
                    x = textX, ty = ty, fontSize = fontSize, font = font,
                    textColor = Theme.TEXT_PRIMARY, accentColor = Theme.ACCENT.withAlpha(0), caret = false,
                )
                else -> Render.text(shown, textX, ty, fontSize, Theme.TEXT_PRIMARY, font)
            }
        }
        Render.popScissor()

        if (hasEyeToggle) {
            val iconSize = 12f
            val eyeX = x + w - iconSize - 6f
            val eyeY = centered(y, h, iconSize)
            val eyeHovered = mouseX in (eyeX - 3f)..(x + w) && mouseY in y..(y + h)
            val eyeColor = if (eyeHovered) Theme.TEXT_PRIMARY else Theme.TEXT_MUTED
            Render.icon(if (isMasked) LucideIcons.eyeOff else LucideIcons.eye, eyeX, eyeY, iconSize, eyeColor)
        }

        suggestionList.refresh(text, focused, x, y, maxOf(w, suggestionMinWidth), h)
    }

    private fun cursorFromClick(mouseX: Float): Int {
        val prefixW = if (prefix.isEmpty()) 0f else Render.textWidth(prefix, fontSize, font)
        val textStartX = bounds.x + Theme.PADDING + prefixW - editor.scrollX
        return editor.cursorFromClick(mouseX, textStartX, fontSize, font).coerceIn(0, editor.length)
    }

    fun contains(mx: Float, my: Float) = bounds.has(mx, my)

    /** Call last. */
    fun renderSuggestions(mouseX: Float, mouseY: Float) = suggestionList.draw(mouseX, mouseY)

    /** Call before the normal click path. */
    fun suggestionClicked(mouseX: Float, mouseY: Float): Boolean {
        suggestionList.mouseClicked(mouseX, mouseY)?.let { take(it) }
        return suggestionList.consumedClick
    }

    fun suggestionScrolled(wheel: MouseScrollInput): Boolean = suggestionList.mouseScrolled(wheel)

    override fun mouseScrolled(wheel: MouseScrollInput): Boolean = suggestionScrolled(wheel)

    // Bypasses the [text] setter, which would reopen the list.
    private fun take(candidate: String) {
        editor.text = candidate
        editor.cursor = editor.length
        editor.anchor = editor.cursor
        reportChange()
    }

    override fun mouseClicked(mouse: MouseButtonInput): Boolean {
        if (mouse.isLeft && suggestionClicked(mouse.x, mouse.y)) return true
        if (!bounds.has(mouse.x, mouse.y)) {
            if (blurOnClickOutside) focused = false
            return false
        }
        if (mouse.isLeft) {
            if (hasEyeToggle) {
                val iconSize = 12f
                val eyeX = bounds.x + bounds.w - iconSize - 6f
                if (mouse.x in (eyeX - 4f)..(bounds.x + bounds.w)) {
                    isMasked = !isMasked
                    return true
                }
            }
            val wasFocused = focused
            focused = true
            dragging = true
            if (wasFocused) {
                val prefixW = if (prefix.isEmpty()) 0f else Render.textWidth(prefix, fontSize, font)
                val textStartX = bounds.x + Theme.PADDING + prefixW - editor.scrollX
                editor.cursorFromClick(mouse.x, textStartX, fontSize, font).let { pos ->
                    editor.cursor = pos
                    editor.anchor = pos
                }
            }
            return true
        }
        return false
    }

    override fun mouseReleased(mouse: MouseButtonInput): Boolean {
        if (suggestionList.mouseReleased()) return true
        if (mouse.isLeft) dragging = false
        return false
    }

    override fun mouseDragged(drag: MouseDragInput): Boolean {
        if (suggestionList.mouseDragged(drag.y)) return true
        if (!focused || !dragging || !drag.isLeft) return false
        editor.cursor = cursorFromClick(drag.x)
        return true
    }

    override fun keyPressed(key: KeyInput): Boolean {
        if (!focused) return false

        if (suggestionList.isOpen) {
            when (key.code) {
                Keys.DOWN -> { suggestionList.moveSelection(1); return true }
                Keys.UP -> { suggestionList.moveSelection(-1); return true }
                Keys.TAB -> {
                    suggestionList.acceptHighlighted(orSingle = true)?.let { take(it) }
                    return true
                }
            }
        }

        if (editor.keyPressed(key)) {
            invalid = false
            suggestionList.undismiss()
            reportChange()
            return true
        }
        when (key.code) {
            Keys.ENTER, Keys.KP_ENTER -> {
                val chosen = if (suggestionList.hasSelection) suggestionList.acceptHighlighted(orSingle = false) else null
                if (chosen != null) take(chosen) else onEnter()
            }
            Keys.ESCAPE -> onEscape()
            else -> return false
        }
        return true
    }

    override fun charTyped(typed: CharInput): Boolean {
        if (!focused || typed.char.isISOControl()) return false
        if (editor.charTyped(typed.char)) {
            invalid = false
            suggestionList.undismiss()
            reportChange()
        }
        return true
    }

    // Only real text changes are reported.
    private fun reportChange() {
        val now = editor.text
        if (now == reportedText) return
        reportedText = now
        onTextChanged(now)
    }

    fun clearBounds() {
        bounds.clear()
        suggestionList.clearBounds()
    }

    fun clear() {
        text = ""
        focused = false
        suggestionList.undismiss()
        clearBounds()
    }
}
