package org.blackaddons.platform.compat

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.KeyEvent
import org.lwjgl.sdl.SDLKeyboard
import org.lwjgl.sdl.SDLKeycode
import org.lwjgl.sdl.SDLMouse

object NativeKeys {

    val KEYBOARD: InputConstants.Type = InputConstants.Type.KEYBOARD

    fun isDown(key: InputConstants.Key): Boolean = when (key.type) {
        InputConstants.Type.MOUSE -> SDLMouse.SDL_GetMouseState(null, null) and (1 shl (key.value - 1)) != 0
        InputConstants.Type.KEYBOARD -> InputConstants.isKeyDown(key.value)
    }

    fun keyName(key: Int): String = KEYBOARD.getOrCreate(key).displayName.string

    // [key] is an SDL scancode, screen shortcuts read the typed keycode. Client thread.
    fun keyEvent(key: Int): KeyEvent =
        KeyEvent(key, SDLKeyboard.SDL_GetKeyFromScancode(key, SDLKeycode.SDL_KMOD_NONE.toShort(), false), 0)
}
