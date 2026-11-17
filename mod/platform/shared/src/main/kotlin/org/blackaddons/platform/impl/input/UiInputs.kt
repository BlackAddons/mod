package org.blackaddons.platform.impl.input

import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import org.blackaddons.api.ui.widget.CharInput
import org.blackaddons.api.ui.widget.KeyInput
import org.blackaddons.api.ui.widget.MouseButtonInput
import org.blackaddons.api.ui.widget.MouseDragInput

// Minecraft's input records as the toolkit's, with the button and modifiers in Keys' numbering.

internal fun MouseButtonEvent.toUi() = MouseButtonInput(x.toFloat(), y.toFloat(), MinecraftKeyNames.button(buttonInfo.button()))

internal fun MouseButtonEvent.toUiDrag(dx: Double, dy: Double) =
    MouseDragInput(x.toFloat(), y.toFloat(), MinecraftKeyNames.button(buttonInfo.button()), dx.toFloat(), dy.toFloat())

internal fun KeyEvent.toUi() = KeyInput(key(), MinecraftKeyNames.modifiers(modifiers()))

internal fun CharacterEvent.toUi() = CharInput(codepoint().toChar(), 0)
