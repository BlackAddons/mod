package org.blackaddons.api.ui.widget.text

import org.blackaddons.api.ui.anim.Animation
import org.blackaddons.api.ui.anim.isVisible
import org.blackaddons.api.ui.render.Font
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.render.RichSpan
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.theme.Theme.withAlpha
import java.awt.Color
import kotlin.math.abs

/**
 * Layout and painting for a [TextBuffer]: caret/selection, horizontal scroll and per-char fade. Measured
 * every frame since each char's width animates.
 */
internal class TextEditorView {
    var scrollX = 0f
        private set

    private val selectionFrom = Animation(0f, Theme.ANIM_TEXT.toLong())
    private val selectionTo = Animation(0f, Theme.ANIM_TEXT.toLong())
    private val selectionAlpha = Animation(0f, Theme.ANIM_TEXT.toLong())
    private var caretX = 0f
    private var snapNext = false

    var appearance: ((String) -> RichSpan?)? = null

    private fun look(cell: String, maskStr: String?): RichSpan? =
        if (maskStr != null) RichSpan.Text(maskStr) else appearance?.invoke(cell)

    private fun width(cell: String, look: RichSpan?, size: Number, font: Font): Float = when (look) {
        is RichSpan.Picture -> size.toFloat() * look.scale
        is RichSpan.Text -> Render.textWidth(look.text, size, font)
        null -> Render.textWidth(cell, size, font)
    }

    fun snap() {
        snapNext = true
    }

    fun measure(buffer: TextBuffer, cursor: Int, from: Int, to: Int, limitW: Float, size: Number, font: Font, maskChar: Char? = null) {
        buffer.prune()
        val selected = from != to

        if (snapNext) {
            val settled = widths(buffer, cursor, from, to, size, font, animated = false, maskChar = maskChar)
            selectionFrom.set(settled.from)
            selectionTo.set(settled.to)
            selectionAlpha.set(if (selected) 1f else 0f)
            snapNext = false
        }

        val widths = widths(buffer, cursor, from, to, size, font, animated = true, maskChar = maskChar)
        caretX = widths.caret
        // Nothing selected collapses the highlight into the caret.
        selectionFrom.animateTo(if (selected) widths.from else widths.caret)
        selectionTo.animateTo(if (selected) widths.to else widths.caret)
        selectionAlpha.animateTo(if (selected) 1f else 0f)

        scrollX = when {
            caretX - scrollX > limitW -> caretX - limitW
            caretX - scrollX < 0f -> caretX
            else -> scrollX
        }.coerceIn(0f, (widths.total - limitW).coerceAtLeast(0f))
    }

    fun draw(
        buffer: TextBuffer,
        x: Float,
        ty: Float,
        size: Number,
        font: Font,
        textColor: Color,
        accentColor: Color,
        caretHidden: Boolean,
        maskChar: Char? = null,
    ) {
        val originX = x - scrollX
        val lineHeight = Render.textHeight(size, font)

        val alpha = selectionAlpha.get()
        val from = selectionFrom.get()
        val to = selectionTo.get()
        if (alpha.isVisible && to - from > 0.1f) {
            val color = accentColor.withAlpha((accentColor.alpha * alpha).toInt())
            Render.rect(originX + from, ty - 1f, to - from, lineHeight + 2f, color)
        }

        var charX = originX
        val maskStr = maskChar?.toString()
        for (char in buffer.drawn) {
            val factor = char.anim.get()
            if (factor <= 0f) continue
            val look = look(char.char, maskStr)
            val w = width(char.char, look, size, font)
            val color = textColor.withAlpha((textColor.alpha * factor).toInt())
            when (look) {
                // After a zero-width space so the picture sits on the text's line box.
                is RichSpan.Picture -> Render.richText(listOf(RichSpan.Text(ZWSP), look), charX, ty, w + 1f, size, color, font)
                is RichSpan.Text -> Render.text(look.text, charX, ty, size, color, font)
                null -> Render.text(char.char, charX, ty, size, color, font)
            }
            charX += w * factor
        }

        if (!caretHidden) Render.rect(originX + caretX, ty, 1f, lineHeight, textColor)
    }

    fun cursorFromClick(buffer: TextBuffer, mx: Float, textStartX: Float, size: Number, font: Font, maskChar: Char? = null): Int {
        var best = 0
        var bestDistance = Float.MAX_VALUE
        var x = textStartX
        var logical = 0
        val maskStr = maskChar?.toString()
        for (char in buffer.drawn) {
            val distance = abs(x - mx)
            if (distance < bestDistance) {
                bestDistance = distance
                best = logical
            }
            x += width(char.char, look(char.char, maskStr), size, font) * char.anim.get()
            if (char.alive) logical++
        }
        return if (abs(x - mx) < bestDistance) logical else best
    }

    private class Widths(val caret: Float, val from: Float, val to: Float, val total: Float)

    private fun widths(
        buffer: TextBuffer,
        cursor: Int,
        from: Int,
        to: Int,
        size: Number,
        font: Font,
        animated: Boolean,
        maskChar: Char? = null,
    ): Widths {
        var caretW = 0f
        var fromW = 0f
        var toW = 0f
        var total = 0f
        var logical = 0
        val maskStr = maskChar?.toString()
        for (char in buffer.drawn) {
            val w = width(char.char, look(char.char, maskStr), size, font) * (if (animated) char.anim.get() else 1f)
            if (logical < cursor) caretW += w
            if (logical < from) fromW += w
            if (logical < to) toW += w
            total += w
            if (char.alive) logical++
        }
        return Widths(caretW, fromW, toW, total)
    }

    private companion object {
        val ZWSP = Char(0x200B).toString()
    }
}
