package org.blackaddons.api.ui.widget.text

internal class TextHistory {
    class Snapshot(val text: String, val cursor: Int, val anchor: Int)

    private val states = mutableListOf<Snapshot>()
    private var index = -1

    fun push(text: String, cursor: Int, anchor: Int) {
        // Pushed after every keystroke, even no-ops; skip duplicates so Ctrl+Z isn't wasted.
        states.getOrNull(index)?.let {
            if (it.text == text && it.cursor == cursor && it.anchor == anchor) return
        }
        while (states.size > index + 1) states.removeAt(states.lastIndex)
        states += Snapshot(text, cursor, anchor)
        index++
        if (states.size > MAX_STATES) {
            states.removeAt(0)
            index--
        }
    }

    fun undo(): Snapshot? = if (index > 0) states[--index] else null

    fun redo(): Snapshot? = if (index < states.lastIndex) states[++index] else null

    private companion object {
        const val MAX_STATES = 50
    }
}
