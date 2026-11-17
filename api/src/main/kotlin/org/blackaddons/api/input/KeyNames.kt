package org.blackaddons.api.input

/**
 * Native key codes for the running version (memory only). Saved data uses Minecraft key names
 * (`key.keyboard.f`). Mouse buttons count from 0.
 */
interface KeyNames {
    /** e.g. "F" or "Left Shift". */
    fun keyName(keyCode: Int): String

    /** e.g. `"key.keyboard.f5"`. For autocomplete. */
    fun allKeyNames(): List<String> = emptyList()

    fun isKeyDown(keyCode: Int): Boolean

    fun isMouseDown(button: Int): Boolean

    fun code(name: String): Int

    fun name(code: Int): String
}
