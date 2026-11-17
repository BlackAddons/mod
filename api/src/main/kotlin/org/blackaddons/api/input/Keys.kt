package org.blackaddons.api.input

import org.blackaddons.api.Platform

/** Resolved to native codes through Minecraft key names. Mouse buttons count from 0. */
object Keys {
    const val UNKNOWN = -1
    val A by key("key.keyboard.a")
    val C by key("key.keyboard.c")
    val D by key("key.keyboard.d")
    val F by key("key.keyboard.f")
    val G by key("key.keyboard.g")
    val V by key("key.keyboard.v")
    val X by key("key.keyboard.x")
    val Y by key("key.keyboard.y")
    val Z by key("key.keyboard.z")
    val DIGIT_1 by key("key.keyboard.1")
    val DIGIT_2 by key("key.keyboard.2")
    val DIGIT_3 by key("key.keyboard.3")
    val DIGIT_4 by key("key.keyboard.4")
    val DIGIT_5 by key("key.keyboard.5")
    val ESCAPE by key("key.keyboard.escape")
    val ENTER by key("key.keyboard.enter")
    val TAB by key("key.keyboard.tab")
    val BACKSPACE by key("key.keyboard.backspace")
    val DELETE by key("key.keyboard.delete")
    val RIGHT by key("key.keyboard.right")
    val LEFT by key("key.keyboard.left")
    val DOWN by key("key.keyboard.down")
    val UP by key("key.keyboard.up")
    val HOME by key("key.keyboard.home")
    val END by key("key.keyboard.end")
    val KP_ENTER by key("key.keyboard.keypad.enter")
    val LEFT_SHIFT by key("key.keyboard.left.shift")
    val LEFT_CONTROL by key("key.keyboard.left.control")
    val LEFT_ALT by key("key.keyboard.left.alt")
    val RIGHT_SHIFT by key("key.keyboard.right.shift")
    val RIGHT_CONTROL by key("key.keyboard.right.control")
    val RIGHT_ALT by key("key.keyboard.right.alt")

    const val MOUSE_LEFT = 0
    const val MOUSE_RIGHT = 1
    const val MOUSE_MIDDLE = 2

    const val MOD_SHIFT = 0x1
    const val MOD_CONTROL = 0x2
    const val MOD_ALT = 0x4
    const val MOD_SUPER = 0x8
    const val MOD_CAPS_LOCK = 0x10
    const val MOD_NUM_LOCK = 0x20

    const val RELEASE = 0
    const val PRESS = 1
    const val REPEAT = 2

    fun isDown(key: Int): Boolean = Platform.keyNames.isKeyDown(key)

    fun isMouseDown(button: Int): Boolean = Platform.keyNames.isMouseDown(button)

    fun name(key: Int): String = Platform.keyNames.keyName(key)

    private fun key(name: String) = lazy { Platform.keyNames.code(name) }
}
