package org.blackaddons.api.event.input

import org.blackaddons.api.input.Keys

/** Modifier keys held at input time. Prefer these to polling the keyboard. Mirrors Minecraft's `InputWithModifiers`. */
interface WithModifiers {

    /** Raw `Keys.MOD_*` bitmask for combinations the flags below don't cover. */
    val modifiers: Int

    val ctrl: Boolean get() = modifiers and Keys.MOD_CONTROL != 0
    val shift: Boolean get() = modifiers and Keys.MOD_SHIFT != 0
    val alt: Boolean get() = modifiers and Keys.MOD_ALT != 0
}
