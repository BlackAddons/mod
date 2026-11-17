package org.blackaddons.api.ui.screen.color
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.text.TextInput
import java.awt.Color

/** `#RRGGBB` field under a [ColorArea]. Enter commits (or marks invalid and keeps editing), Esc reverts. */
internal class HexField(private val area: ColorArea, private val allowAlpha: Boolean) {

    val width = if (allowAlpha) 92f else 72f

    val input = TextInput(
        prefix = "#",
        maxLength = if (allowAlpha) 8 else 6,
        charFilter = { it in '0'..'9' || it in 'A'..'F' },
        charTransformer = { it.uppercaseChar() },
    ).apply {
        fontSize = Theme.FONT_BODY
        font = Fonts.medium
        blurOnClickOutside = false
        displayText = { format() }
        onFocusGained = {
            editor.text = format()
            editor.cursor = editor.text.length
            editor.anchor = editor.cursor
        }
        onEnter = { commit() }
        onEscape = { cancel() }
    }

    /** Without `#`. */
    private fun format(): String {
        val c = area.color
        return if (allowAlpha) "%02X%02X%02X%02X".format(c.red, c.green, c.blue, c.alpha)
        else "%02X%02X%02X".format(c.red, c.green, c.blue)
    }

    /** `RRGGBB` keeps the current alpha. */
    fun commit() {
        val t = input.text
        val acceptable = t.length == 6 || (allowAlpha && t.length == 8)
        val parsed = if (acceptable) t.toLongOrNull(16) else null
        if (parsed == null) {
            input.invalid = true
            return
        }
        val color = if (t.length == 8) {
            Color(
                ((parsed shr 24) and 0xFF).toInt(),
                ((parsed shr 16) and 0xFF).toInt(),
                ((parsed shr 8) and 0xFF).toInt(),
                (parsed and 0xFF).toInt(),
            )
        } else {
            Color(
                ((parsed shr 16) and 0xFF).toInt(),
                ((parsed shr 8) and 0xFF).toInt(),
                (parsed and 0xFF).toInt(),
                area.color.alpha,
            )
        }
        input.invalid = false
        input.focused = false
        area.setColor(color)
    }

    fun cancel() {
        input.invalid = false
        input.text = ""
        input.focused = false
    }
}
