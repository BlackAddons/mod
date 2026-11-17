package org.blackaddons.api.ui.widget.text

import org.blackaddons.api.ui.render.Font
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import org.blackaddons.api.util.graphemes
import java.awt.Color

/** Label that animates character changes like [TextEditor]. */
class AnimatedText(
    initialText: String = "",
    var fontSize: Float = Theme.FONT_TITLE,
    var font: Font = Fonts.bold,
    /** Null follows the theme. */
    var color: Color? = null,
) {
    private val buffer = TextBuffer()

    var text: String
        get() = buffer.text
        set(value) {
            val current = buffer.text
            if (current == value) return

            val currentGraphemes = current.graphemes()
            val newGraphemes = value.graphemes()

            var common = 0
            while (common < currentGraphemes.size && common < newGraphemes.size && currentGraphemes[common] == newGraphemes[common]) {
                common++
            }

            if (currentGraphemes.size > common) {
                buffer.fadeOut(common, currentGraphemes.size)
            }

            if (newGraphemes.size > common) {
                val newSuffix = newGraphemes.subList(common, newGraphemes.size).joinToString("")
                buffer.insert(common, newSuffix)
            }
        }

    init {
        buffer.text = initialText
    }

    val width: Float
        get() {
            buffer.prune()
            var w = 0f
            for (char in buffer.drawn) {
                val factor = char.anim.get()
                if (factor > 0f) {
                    w += Render.textWidth(char.char, fontSize, font) * factor
                }
            }
            return w
        }

    fun render(x: Float, y: Float) {
        buffer.prune()
        val base = color ?: Theme.TEXT_PRIMARY
        var charX = x
        for (char in buffer.drawn) {
            val factor = char.anim.get()
            if (factor <= 0f) continue
            val charColor = base.withAlpha((base.alpha * factor).toInt())
            Render.text(char.char, charX, y, fontSize, charColor, font)
            charX += Render.textWidth(char.char, fontSize, font) * factor
        }
    }

    fun snap(text: String = this.text) {
        buffer.text = text
    }
}
