package org.blackaddons.api.input

import org.blackaddons.api.testing.TestPlatform

object FakeKeyNames : KeyNames {
    private val codes = mapOf(
        "key.keyboard.f" to 70,
        "key.keyboard.g" to 71,
        "key.keyboard.escape" to 256,
        "key.keyboard.v" to 25,
        "key.keyboard.backspace" to 259,
        "key.keyboard.delete" to 261,
        "key.keyboard.home" to 268,
    )

    fun install() {
        TestPlatform.keyNames = this
    }

    override fun keyName(keyCode: Int): String = name(keyCode)

    override fun isKeyDown(keyCode: Int): Boolean = false

    override fun isMouseDown(button: Int): Boolean = false

    override fun code(name: String): Int = codes[name] ?: Keys.UNKNOWN

    override fun name(code: Int): String = codes.entries.first { it.value == code }.key
}
