package org.blackaddons.platform.impl.input
import com.mojang.blaze3d.platform.InputConstants
import org.blackaddons.api.input.KeyNames
import org.blackaddons.api.input.Keys
import org.blackaddons.platform.compat.NativeKeys
import org.blackaddons.platform.mixin.input.InputConstantsKeyAccessor

object MinecraftKeyNames : KeyNames {

    override fun keyName(keyCode: Int): String = NativeKeys.keyName(keyCode)

    override fun allKeyNames(): List<String> = InputConstantsKeyAccessor.`blackaddons$nameMap`().keys
        .filter { it.startsWith("key.keyboard.") && it != "key.keyboard.unknown" }
        .sorted()

    // InputConstantsMixin makes isKeyDown report SyntheticKeys too.
    override fun isKeyDown(keyCode: Int): Boolean = NativeKeys.isDown(NativeKeys.KEYBOARD.getOrCreate(keyCode))

    override fun isMouseDown(button: Int): Boolean =
        NativeKeys.isDown(InputConstants.Type.MOUSE.getOrCreate(nativeButton(button)))

    override fun code(name: String): Int =
        runCatching { InputConstants.getKey(name) }.getOrNull()?.takeIf { it != InputConstants.UNKNOWN }?.value ?: Keys.UNKNOWN

    override fun name(code: Int): String = NativeKeys.KEYBOARD.getOrCreate(code).name

    fun button(native: Int): Int = when (native) {
        InputConstants.MOUSE_BUTTON_LEFT -> Keys.MOUSE_LEFT
        InputConstants.MOUSE_BUTTON_RIGHT -> Keys.MOUSE_RIGHT
        InputConstants.MOUSE_BUTTON_MIDDLE -> Keys.MOUSE_MIDDLE
        else -> native - InputConstants.MOUSE_BUTTON_LEFT
    }

    private fun nativeButton(button: Int): Int = when (button) {
        Keys.MOUSE_LEFT -> InputConstants.MOUSE_BUTTON_LEFT
        Keys.MOUSE_RIGHT -> InputConstants.MOUSE_BUTTON_RIGHT
        Keys.MOUSE_MIDDLE -> InputConstants.MOUSE_BUTTON_MIDDLE
        else -> button + InputConstants.MOUSE_BUTTON_LEFT
    }

    fun modifiers(native: Int): Int =
        (if (native and InputConstants.MOD_SHIFT != 0) Keys.MOD_SHIFT else 0) or
            (if (native and InputConstants.MOD_CONTROL != 0) Keys.MOD_CONTROL else 0) or
            (if (native and InputConstants.MOD_ALT != 0) Keys.MOD_ALT else 0) or
            (if (native and InputConstants.MOD_SUPER != 0) Keys.MOD_SUPER else 0) or
            (if (native and InputConstants.MOD_CAPS_LOCK != 0) Keys.MOD_CAPS_LOCK else 0) or
            (if (native and InputConstants.MOD_NUM_LOCK != 0) Keys.MOD_NUM_LOCK else 0)

    fun action(native: Int): Int = when (native) {
        InputConstants.PRESS -> Keys.PRESS
        InputConstants.REPEAT -> Keys.REPEAT
        else -> Keys.RELEASE
    }
}
