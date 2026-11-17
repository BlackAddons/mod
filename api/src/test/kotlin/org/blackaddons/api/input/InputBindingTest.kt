package org.blackaddons.api.input

import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class InputBindingTest {
    init {
        FakeKeyNames.install()
    }

    @Test
    fun `a GLFW number from an old save resolves through the key name, not as a native code`() {
        assertEquals(InputBinding.keyboard(25), InputBinding.legacy(InputType.KEYBOARD, 86))
        assertEquals(InputBinding.keyboard(25), InputBinding.fromJson(JsonPrimitive(86)))
        assertEquals(InputBinding.keyboard(25), InputBinding.fromJson(JsonParser.parseString("""{"type":"KEYBOARD","key":86}""")))
    }

    @Test
    fun `a GLFW number with no key name comes back unbound`() {
        assertFalse(InputBinding.legacy(InputType.KEYBOARD, 999).isBound)
    }

    @Test
    fun `a key is saved by name and a mouse button by index`() {
        val key = InputBinding.keyboard(25)
        assertEquals("key.keyboard.v", key.toJson().asJsonObject.get("key").asString)
        assertEquals(key, InputBinding.fromJson(key.toJson()))

        val mouse = InputBinding.mouse(Keys.MOUSE_MIDDLE)
        assertEquals(mouse, InputBinding.fromJson(mouse.toJson()))
        assertEquals(mouse, InputBinding.legacy(InputType.MOUSE, Keys.MOUSE_MIDDLE))
    }
}
