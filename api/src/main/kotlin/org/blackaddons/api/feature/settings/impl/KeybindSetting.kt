package org.blackaddons.api.feature.settings.impl

import com.google.gson.JsonElement
import org.blackaddons.api.config.Savable
import org.blackaddons.api.feature.settings.Setting
import org.blackaddons.api.input.InputBinding
import org.blackaddons.api.input.InputType
import org.blackaddons.api.input.Keys

/**
 * Key or mouse button (from 0). [value] is the running version's key code; saved as Minecraft's key name
 * (`"key.keyboard.f"`), old GLFW numbers are still read.
 */
open class KeybindSetting(
    name: String,
    default: Int = Keys.UNKNOWN,
    private val defaultType: InputType = InputType.KEYBOARD,
    id: String = name,
) : Setting<Int>(id, name, default), Savable {

    /** Physically held right now; works outside the event loop. */
    val isPressed: Boolean get() = binding.isPressed

    var type: InputType = defaultType

    // Reset the type too, or a default mouse button becomes the keyboard key with the same number.
    override fun reset() {
        super.reset()
        type = defaultType
    }

    var binding: InputBinding
        get() = InputBinding(type, value)
        set(b) {
            type = b.type
            value = b.code
        }

    val isBound: Boolean get() = binding.isBound

    /** `F`, `MOUSE MIDDLE` or `NONE`. */
    val displayName: String
        get() = when {
            !isBound -> "NONE"
            type == InputType.MOUSE -> when (value) {
                Keys.MOUSE_LEFT -> "MOUSE LEFT"
                Keys.MOUSE_RIGHT -> "MOUSE RIGHT"
                Keys.MOUSE_MIDDLE -> "MOUSE MIDDLE"
                else -> "MOUSE ${value + 1}"
            }
            else -> runCatching { Keys.name(value).uppercase() }.getOrElse { "KEY $value" }
        }

    fun matches(inputType: InputType, code: Int): Boolean = binding.matches(inputType, code)

    fun bindKeyboard(keyCode: Int) {
        type = InputType.KEYBOARD
        value = keyCode
    }

    fun bindMouse(button: Int) {
        type = InputType.MOUSE
        value = button
    }

    fun unbind() {
        type = InputType.KEYBOARD
        value = Keys.UNKNOWN
    }

    override fun write(): JsonElement = binding.toJson()

    override fun read(element: JsonElement?) {
        if (element != null) binding = InputBinding.fromJson(element)
    }
}
