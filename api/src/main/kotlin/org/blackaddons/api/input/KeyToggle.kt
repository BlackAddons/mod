package org.blackaddons.api.input

/**
 * Pressing the key flips [active]. While [hold] is on, it is active only while the key is down. Feed it the key once
 * per tick.
 */
class KeyToggle(private val hold: () -> Boolean) {

    /** Writable, so an owner whose state also changes elsewhere can sync it before [update]. */
    var active: Boolean = false

    private var wasDown = false

    fun update(down: Boolean): Boolean {
        if (hold()) active = down else if (down && !wasDown) active = !active
        wasDown = down
        return active
    }

    /**
     * Call when the key cannot be read, for example while a screen is open. Forgets the key, and in hold mode
     * turns off.
     */
    fun release() {
        wasDown = false
        if (hold()) active = false
    }
}
