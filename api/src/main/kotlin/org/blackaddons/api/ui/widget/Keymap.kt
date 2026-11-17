package org.blackaddons.api.ui.widget

import org.blackaddons.api.input.Keys

/**
 * Panel shortcuts as a table: `keys.on(Keys.F, ctrl = true) { search.focus() }`. Asked after the focused
 * widget declines. Modifiers match exactly; rebinding a combo replaces it.
 */
class Keymap {

    private val bindings = HashMap<Int, Binding>()

    fun on(
        key: Int,
        ctrl: Boolean = false,
        shift: Boolean = false,
        alt: Boolean = false,
        onlyIf: () -> Boolean = { true },
        action: () -> Unit,
    ) {
        bindings[combo(key, mask(ctrl, shift, alt))] = Binding(onlyIf, action)
    }

    /** True if a binding ran. */
    fun handle(key: Int, modifiers: Int): Boolean {
        val binding = bindings[combo(key, modifiers and MODIFIERS)] ?: return false
        if (!binding.onlyIf()) return false
        binding.action()
        return true
    }

    private fun mask(ctrl: Boolean, shift: Boolean, alt: Boolean): Int =
        (if (ctrl) Keys.MOD_CONTROL else 0) or
            (if (shift) Keys.MOD_SHIFT else 0) or
            (if (alt) Keys.MOD_ALT else 0)

    private fun combo(key: Int, modifiers: Int): Int = key shl 3 or modifiers

    private class Binding(val onlyIf: () -> Boolean, val action: () -> Unit)

    private companion object {
        // Caps/Num Lock are state, not chords.
        const val MODIFIERS = Keys.MOD_CONTROL or Keys.MOD_SHIFT or Keys.MOD_ALT
    }
}
