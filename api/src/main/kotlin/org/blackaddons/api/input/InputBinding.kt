package org.blackaddons.api.input

import com.google.gson.*
import org.blackaddons.api.Platform
import java.lang.reflect.Type

/**
 * [code] is the running version's native key code (mouse buttons from 0), so it only lives in memory. Save
 * through [toJson], which writes Minecraft's key name.
 */
data class InputBinding(val type: InputType, val code: Int) {

    val isBound: Boolean get() = if (type == InputType.MOUSE) code >= 0 else code > 0

    val isPressed: Boolean
        get() = isBound && if (type == InputType.MOUSE) Keys.isMouseDown(code) else Keys.isDown(code)

    fun matches(inputType: InputType, code: Int): Boolean = isBound && type == inputType && this.code == code

    fun toJson(): JsonElement = JsonObject().apply {
        addProperty("type", type.name)
        when {
            type == InputType.MOUSE -> addProperty("key", code)
            isBound -> addProperty("key", Platform.keyNames.name(code))
        }
    }

    companion object {
        @JvmField val NONE = InputBinding(InputType.KEYBOARD, Keys.UNKNOWN)

        fun keyboard(code: Int) = InputBinding(InputType.KEYBOARD, code)

        fun mouse(button: Int) = InputBinding(InputType.MOUSE, button)

        /** Pre-name format: a keyboard key is a GLFW code. */
        fun legacy(type: InputType, code: Int): InputBinding =
            if (type == InputType.MOUSE) mouse(code) else keyboard(GlfwKeyNames.native(code))

        /** Also reads the old bare-number forms. */
        fun fromJson(element: JsonElement?): InputBinding {
            if (element == null || element.isJsonNull) return NONE
            if (element.isJsonPrimitive) {
                val p = element.asJsonPrimitive
                return if (p.isNumber) legacy(InputType.KEYBOARD, p.asInt) else NONE
            }
            if (!element.isJsonObject) return NONE
            val obj = element.asJsonObject
            val type = obj.get("type")?.takeIf { it.isJsonPrimitive }?.asString
                ?.let { runCatching { InputType.valueOf(it) }.getOrNull() } ?: InputType.KEYBOARD
            val key = obj.get("key")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: return InputBinding(type, Keys.UNKNOWN)
            return when {
                type == InputType.MOUSE -> mouse(key.asInt)
                key.isNumber -> legacy(type, key.asInt)
                else -> keyboard(Platform.keyNames.code(key.asString))
            }
        }
    }

    /** Lets Gson models save it by name. */
    object GsonAdapter : JsonSerializer<InputBinding>, JsonDeserializer<InputBinding> {
        override fun serialize(src: InputBinding, typeOfSrc: Type, context: JsonSerializationContext): JsonElement = src.toJson()

        override fun deserialize(json: JsonElement?, typeOfT: Type, context: JsonDeserializationContext): InputBinding = fromJson(json)
    }
}
