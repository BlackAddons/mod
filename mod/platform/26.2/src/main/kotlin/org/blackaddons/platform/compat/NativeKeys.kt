package org.blackaddons.platform.compat

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

object NativeKeys {

    val KEYBOARD: InputConstants.Type = InputConstants.Type.KEYSYM

    fun isDown(key: InputConstants.Key): Boolean {
        val window = Minecraft.getInstance().window
        return when (key.type) {
            InputConstants.Type.MOUSE -> GLFW.glfwGetMouseButton(window.handle(), key.value) == GLFW.GLFW_PRESS
            InputConstants.Type.KEYSYM -> InputConstants.isKeyDown(window, key.value)
            else -> false
        }
    }

    // GLFW key plus scancode, like a physical press. Client thread.
    fun keyEvent(key: Int): KeyEvent = KeyEvent(key, GLFW.glfwGetKeyScancode(key), 0)

    /**
     * Asked fresh every time. Minecraft caches each `Key`'s display name over a layout-dependent GLFW lookup, so a key
     * kept its first layout's name (`Z` next to `И`). Not cached here because GLFW has no layout-change callback. One
     * JNI call per drawn row.
     */
    fun keyName(key: Int): String {
        // GLFW names keypad keys by the character ("8"), we use "Keypad 8" like 26.3.
        if (key in GLFW.GLFW_KEY_KP_0..GLFW.GLFW_KEY_KP_EQUAL) {
            return Component.translatable(KEYBOARD.getOrCreate(key).name).string
        }
        return GLFW.glfwGetKeyName(key, 0)
            // Non-printable keys have stable, localized translatable names.
            ?: KEYBOARD.getOrCreate(key).displayName.string
    }
}
