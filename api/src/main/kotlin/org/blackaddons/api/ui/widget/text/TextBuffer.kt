package org.blackaddons.api.ui.widget.text

import org.blackaddons.api.ui.anim.Animation
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.util.graphemes

internal class AnimChar(val char: String, initial: Float, target: Float) {
    val anim = Animation(initial, Theme.ANIM_TEXT.toLong())

    init {
        anim.animateTo(target)
    }

    // Fading out: still drawn, but no longer in the logical text.
    val alive: Boolean get() = anim.target == 1f

    fun fadeOut() = anim.animateTo(0f)
}

/** Line of individually animated characters. Logical indices count only [AnimChar.alive] chars. */
internal class TextBuffer(private val cells: (String) -> List<String> = String::graphemes) {
    private val chars = mutableListOf<AnimChar>()

    /** Draw order, fading ones included. */
    val drawn: List<AnimChar> get() = chars

    val length: Int get() = chars.count { it.alive }

    var text: String
        get() = chars.filter { it.alive }.joinToString("") { it.char }
        set(value) {
            chars.clear()
            for (cell in cells(value)) {
                chars += AnimChar(cell, 1f, 1f)
            }
        }

    fun textIn(from: Int, until: Int): String = range(from, until).joinToString("") { it.char }

    fun fadeOut(from: Int, until: Int) = range(from, until).forEach { it.fadeOut() }

    /** Returns the index after [str]. Joining cells (ZWJ sequences, skin tones) merge with their neighbor. */
    fun insert(at: Int, str: String): Int {
        val alive = chars.filter { it.alive }
        val index = at.coerceIn(0, alive.size)
        val before = alive.getOrNull(index - 1)
        val after = alive.getOrNull(index)
        val added = cells(str)
        val merged = cells((before?.char ?: "") + str + (after?.char ?: ""))
        val separate = listOfNotNull(before?.char) + added + listOfNotNull(after?.char)
        if (merged == separate) {
            chars.addAll(physical(index), added.map { AnimChar(it, 0f, 1f) })
            return index + added.size
        }

        val start = if (before != null) index - 1 else index
        val first = physical(start)
        before?.let { chars.remove(it) }
        after?.let { chars.remove(it) }
        chars.addAll(first, merged.map { AnimChar(it, 1f, 1f) })
        // Past the cell with the last inserted char, even if that cell absorbed the next one.
        val end = (before?.char?.length ?: 0) + str.length
        var offset = 0
        var caret = start
        for (cell in merged) {
            if (offset >= end) break
            offset += cell.length
            caret++
        }
        return caret
    }

    fun prune() {
        chars.removeAll { !it.anim.isRunning && !it.alive }
    }

    /** Forward for positive [direction]. */
    fun wordBoundary(from: Int, direction: Int): Int {
        val alive = chars.filter { it.alive }
        var pos = from.coerceIn(0, alive.size)
        if (direction > 0) {
            while (pos < alive.size && alive[pos].char == " ") pos++
            while (pos < alive.size && alive[pos].char != " ") pos++
        } else {
            while (pos > 0 && alive[pos - 1].char == " ") pos--
            while (pos > 0 && alive[pos - 1].char != " ") pos--
        }
        return pos
    }

    private fun range(from: Int, until: Int): List<AnimChar> {
        val alive = chars.filter { it.alive }
        val start = from.coerceIn(0, alive.size)
        return alive.subList(start, until.coerceIn(start, alive.size))
    }

    private fun physical(logical: Int): Int {
        val target = logical.coerceAtLeast(0)
        var seen = 0
        for ((i, char) in chars.withIndex()) {
            if (!char.alive) continue
            if (seen == target) return i
            seen++
        }
        return chars.size
    }
}
