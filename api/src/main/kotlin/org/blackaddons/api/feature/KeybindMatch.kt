package org.blackaddons.api.feature

import org.blackaddons.api.event.input.KeyInputEvent
import org.blackaddons.api.event.input.MouseClickEvent
import org.blackaddons.api.feature.settings.impl.KeybindSetting
import org.blackaddons.api.input.InputType

fun KeybindSetting.matches(event: KeyInputEvent): Boolean =
    isBound && type == InputType.KEYBOARD && value == event.key && event.isPress

fun KeybindSetting.matches(event: MouseClickEvent): Boolean =
    isBound && type == InputType.MOUSE && value == event.button && event.isPress
