package org.blackaddons.api.ui.widget.text

import org.blackaddons.api.Platform
import org.blackaddons.api.input.Keys
import org.blackaddons.api.ui.render.Font
import org.blackaddons.api.ui.render.RichSpan
import org.blackaddons.api.ui.widget.KeyInput
import org.blackaddons.api.ui.widget.setting.StringWidget
import org.blackaddons.api.util.graphemes
import java.awt.Color
import kotlin.math.max
import kotlin.math.min

/**
 * Caret, selection, clipboard and undo over one line; wrapped by [TextInput] and [StringWidget]. Chars fade
 * in/out, so indices always count committed chars only.
 */
class TextEditor(
    var maxLength: Int = Int.MAX_VALUE,
    var charFilter: (Char) -> Boolean = { true },
    var charTransformer: (Char) -> Char = { it },
) {
    /** Caret/Backspace units: graphemes unless the host knows longer tokens. */
    var cells: (String) -> List<String> = String::graphemes

    /** Custom look for a cell ([RichSpan.Picture] for emoji). Ignored while masked. */
    var appearance: ((String) -> RichSpan?)?
        get() = view.appearance
        set(value) { view.appearance = value }

    private val buffer = TextBuffer { cells(it) }
    private val history = TextHistory()
    private val view = TextEditorView()

    private var lastEditMs = System.currentTimeMillis()
    private var pendingHighSurrogate: Char? = null

    var cursor = 0
        set(value) {
            field = value
            lastEditMs = System.currentTimeMillis()
        }

    /** Fixed end of the selection; equals [cursor] when nothing is selected. */
    var anchor = 0
        set(value) {
            field = value
            lastEditMs = System.currentTimeMillis()
        }

    var text: String
        get() = buffer.text
        set(value) {
            pendingHighSurrogate = null
            buffer.text = value
            cursor = buffer.length
            anchor = cursor
            pushHistory()
            view.snap()
        }

    val length: Int get() = buffer.length
    val selectionStart: Int get() = min(cursor, anchor)
    val selectionEnd: Int get() = max(cursor, anchor)
    val hasSelection: Boolean get() = selectionStart != selectionEnd
    val selectedText: String get() = buffer.textIn(selectionStart, selectionEnd)

    /** Needed to map clicks to a caret. */
    val scrollX: Float get() = view.scrollX

    /** Steady right after an edit. */
    val caretHidden: Boolean get() {
        val idle = System.currentTimeMillis() - lastEditMs
        return idle >= BLINK_MS && ((idle - BLINK_MS) / BLINK_MS) % 2 == 1L
    }

    /** Skip the tween - on focus or after undo. */
    fun snap() = view.snap()

    fun selectAll() {
        anchor = 0
        cursor = length
    }

    /** False if nothing survived [charFilter] / [maxLength]. */
    fun insert(str: String): Boolean {
        pendingHighSurrogate = null
        val room = maxLength - (length - (selectionEnd - selectionStart))
        if (room <= 0) return false

        val clusters = cells(str)
        val accepted = ArrayList<String>(clusters.size)
        for (cluster in clusters) {
            if (accepted.size >= room) break
            if (cluster.length == 1) {
                val c = cluster[0]
                if (c >= ' ' && charFilter(c)) {
                    accepted += charTransformer(c).toString()
                }
            } else {
                if (cluster.all { it >= ' ' && charFilter(it) }) {
                    accepted += cluster.map(charTransformer).joinToString("")
                }
            }
        }
        if (accepted.isEmpty()) return false

        deleteSelection()
        cursor = buffer.insert(cursor, accepted.joinToString(""))
        anchor = cursor
        return true
    }

    fun deleteSelection(): Boolean {
        if (!hasSelection) return false
        deleteRange(selectionStart, selectionEnd)
        return true
    }

    /** Negative deletes backwards. */
    fun delete(offset: Int) {
        if (offset < 0) deleteRange(cursor + offset, cursor) else deleteRange(cursor, cursor + offset)
    }

    fun pushHistory() = history.push(text, cursor, anchor)

    fun undo() {
        history.undo()?.let(::restore)
    }

    fun redo() {
        history.redo()?.let(::restore)
    }

    var maskChar: Char? = null

    /** Call before [draw]. */
    fun updateLayout(limitW: Float, fontSize: Number, font: Font) =
        view.measure(buffer, cursor, selectionStart, selectionEnd, limitW, fontSize, font, maskChar)

    fun draw(x: Float, ty: Float, fontSize: Number, font: Font, textColor: Color, accentColor: Color, caret: Boolean = true) =
        view.draw(buffer, x, ty, fontSize, font, textColor, accentColor, caretHidden || !caret, maskChar)

    fun cursorFromClick(mx: Float, textStartX: Float, size: Number, font: Font): Int =
        view.cursorFromClick(buffer, mx, textStartX, size, font, maskChar)

    fun keyPressed(key: KeyInput): Boolean {
        pendingHighSurrogate = null
        val ctrl = (key.modifiers and (Keys.MOD_CONTROL or Keys.MOD_SUPER)) != 0
        val shift = key.shift

        if (ctrl) when (key.code) {
            Keys.A -> { selectAll(); return true }
            Keys.C -> if (hasSelection) { Platform.clipboard.copy(selectedText); return true }
            Keys.X -> if (hasSelection) {
                Platform.clipboard.copy(selectedText)
                deleteSelection()
                pushHistory()
                return true
            }
            Keys.V -> {
                if (insert(Platform.clipboard.paste())) pushHistory()
                return true
            }
            Keys.Z -> { undo(); return true }
            Keys.Y -> { redo(); return true }
        }

        when (key.code) {
            Keys.BACKSPACE -> erase(wholeWord = ctrl, forward = false)
            Keys.DELETE -> erase(wholeWord = ctrl, forward = true)
            Keys.LEFT -> moveCaret(if (ctrl) buffer.wordBoundary(cursor, -1) else cursor - 1, shift)
            Keys.RIGHT -> moveCaret(if (ctrl) buffer.wordBoundary(cursor, 1) else cursor + 1, shift)
            Keys.HOME -> moveCaret(0, shift)
            Keys.END -> moveCaret(length, shift)
            else -> return false
        }
        return true
    }

    fun charTyped(ch: Char): Boolean {
        if (Character.isHighSurrogate(ch)) {
            pendingHighSurrogate = ch
            return true
        }
        val fullStr = if (Character.isLowSurrogate(ch)) {
            val high = pendingHighSurrogate
            pendingHighSurrogate = null
            if (high != null) "$high$ch" else ch.toString()
        } else {
            pendingHighSurrogate = null
            ch.toString()
        }
        if (!insert(fullStr)) return false
        pushHistory()
        return true
    }

    private fun moveCaret(to: Int, keepSelection: Boolean) {
        cursor = to.coerceIn(0, length)
        if (!keepSelection) anchor = cursor
    }

    private fun erase(wholeWord: Boolean, forward: Boolean) {
        if (deleteSelection()) {
            pushHistory()
            return
        }
        val atEdge = if (forward) cursor >= length else cursor <= 0
        if (atEdge) return
        when {
            !wholeWord -> delete(if (forward) 1 else -1)
            forward -> deleteRange(cursor, buffer.wordBoundary(cursor, 1))
            else -> deleteRange(buffer.wordBoundary(cursor, -1), cursor)
        }
        pushHistory()
    }

    private fun deleteRange(from: Int, until: Int) {
        val start = max(0, from)
        val end = min(length, until)
        if (start >= end) return
        buffer.fadeOut(start, end)
        cursor = start
        anchor = start
    }

    private fun restore(state: TextHistory.Snapshot) {
        buffer.text = state.text
        cursor = state.cursor
        anchor = state.anchor
        view.snap()
    }

    private companion object {
        /** Caret holds steady this long after an edit, then blinks with this half-period. */
        const val BLINK_MS = 500L
    }
}
