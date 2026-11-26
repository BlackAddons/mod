package org.blackaddons.api.feature.settings.impl

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.blackaddons.api.input.FakeKeyNames
import org.blackaddons.api.input.InputType
import org.blackaddons.api.input.Keys
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.Color

/**
 * User values must survive `write` -> `read`. Also pins the deliberately lossy paths (number snapping,
 * unknown modes) so changing them is a conscious choice.
 */
class SettingsSerializationTest {
    init {
        FakeKeyNames.install()
    }


    @Test
    fun `boolean round trips and ignores a non-primitive`() {
        val s = BooleanSetting("b", default = false)
        s.value = true
        val other = BooleanSetting("b", default = false)
        other.read(s.write())
        assertTrue(other.value)

        other.read(JsonArray())
        assertTrue(other.value) // unchanged
        other.read(null)
        assertTrue(other.value)
    }

    @Test
    fun `string truncates to maxLength on read and ignores a non-string`() {
        val s = StringSetting("s", default = "", maxLength = 4)
        s.read(JsonPrimitive("abcdefg"))
        assertEquals("abcd", s.value)

        s.read(JsonPrimitive(42))
        assertEquals("abcd", s.value)
        s.read(JsonNull.INSTANCE)
        assertEquals("abcd", s.value)
    }

    @Test
    fun `number snaps to step and clamps on both construction and read`() {
        val s = NumberSetting("n", default = 5.7, min = 0.0, max = 10.0, step = 1.0)
        assertEquals(6.0, s.defaultValue) // snapped at construction
        assertEquals(6.0, s.value)

        s.value = 2.4
        assertEquals(2.0, s.value)
        s.value = 100.0
        assertEquals(10.0, s.value) // clamped to max
        s.value = -3.0
        assertEquals(0.0, s.value) // clamped to min

        val other = NumberSetting("n", default = 0.0, min = 0.0, max = 10.0, step = 1.0)
        other.read(JsonPrimitive(7.6))
        assertEquals(8.0, other.value) // read is lossy: snaps
    }

    @Test
    fun `number with zero step only clamps`() {
        val s = NumberSetting("n", default = 0.0, min = 0.0, max = 10.0, step = 0.0)
        s.value = 5.7
        assertEquals(5.7, s.value)
        s.value = 42.0
        assertEquals(10.0, s.value)
    }

    @Test
    fun `mode rejects a default outside its list`() {
        assertThrows(IllegalArgumentException::class.java) {
            ModeSetting("m", default = "x", modes = listOf("a", "b"))
        }
    }

    @Test
    fun `mode round trips and silently rejects an unknown value`() {
        val s = ModeSetting("m", default = "a", modes = listOf("a", "b", "c"))
        s.value = "b"
        val other = ModeSetting("m", default = "a", modes = listOf("a", "b", "c"))
        other.read(s.write())
        assertEquals("b", other.value)

        other.read(JsonPrimitive("zzz")) // not in modes
        assertEquals("b", other.value)
        other.value = "zzz"
        assertEquals("b", other.value)
    }

    @Test
    fun `mode next and previous wrap around`() {
        val s = ModeSetting("m", default = "a", modes = listOf("a", "b", "c"))
        s.previous()
        assertEquals("c", s.value)
        s.next()
        assertEquals("a", s.value)
    }

    @Test
    fun `list write-read is a dedup-and-truncate round trip`() {
        val s = ListSetting("l", itemMaxLength = 4)
        s.add("hello") // truncated to "hell"
        s.add("hell") // duplicate, dropped
        s.add("  bar  ") // trimmed
        s.add("") // empty, dropped
        assertEquals(listOf("hell", "bar"), s.value)

        val other = ListSetting("l", itemMaxLength = 4)
        other.read(s.write())
        assertEquals(listOf("hell", "bar"), other.value)
    }

    @Test
    fun `list entry choices round trip and default entries stay plain strings`() {
        val match = EntryOption("match", listOf("Contains", "Regex", "Exact"))
        val case = EntryOption("case", listOf("Any", "Match"))
        val s = ListSetting("l").apply { entryOptions = listOf(match, case) }
        s.add("a")
        s.add("b")
        s.setChoice("b", match, "Regex")
        s.setChoice("b", case, "Match")
        s.setChoice("a", match, "Nope")
        s.setChoice("missing", match, "Regex")

        val written = s.write().asJsonArray
        assertTrue(written[0].isJsonPrimitive)
        assertEquals("Regex", written[1].asJsonObject.get("match").asString)

        val other = ListSetting("l").apply { entryOptions = listOf(match, case) }
        other.read(written)
        assertEquals(listOf("a", "b"), other.value)
        assertEquals("Contains", other.choiceOf("a", match))
        assertEquals("Regex", other.choiceOf("b", match))
        assertEquals("Match", other.choiceOf("b", case))

        other.setChoice("b", case, "Any")
        other.removeAt(1)
        assertEquals(emptyMap<String, Map<String, String>>(), other.entryChoices)
    }

    @Test
    fun `list read drops unknown choices and objects without a value`() {
        val match = EntryOption("match", listOf("Contains", "Regex"))
        val arr = JsonArray().apply {
            add(JsonObject().apply { addProperty("value", "x"); addProperty("match", "Nope") })
            add(JsonObject().apply { addProperty("match", "Regex") })
        }
        val s = ListSetting("l").apply { entryOptions = listOf(match) }
        s.read(arr)
        assertEquals(listOf("x"), s.value)
        assertEquals("Contains", s.choiceOf("x", match))
    }

    @Test
    fun `list read filters non-strings, truncates and dedups`() {
        val arr = JsonArray().apply {
            add(JsonPrimitive("abcdef")) // -> "abcd"
            add(JsonPrimitive("abcd")) // duplicate after truncation
            add(JsonPrimitive(7)) // dropped
            add(JsonPrimitive("keep"))
        }
        val s = ListSetting("l", itemMaxLength = 4)
        s.read(arr)
        assertEquals(listOf("abcd", "keep"), s.value)
    }

    @Test
    fun `list read ignores a non-array`() {
        val s = ListSetting("l", default = listOf("a"))
        s.read(JsonPrimitive("nope"))
        assertEquals(listOf("a"), s.value)
    }

    @Test
    fun `color persists as a hex primitive while chroma is off`() {
        val withAlpha = ColorSetting("c", Color(0x11, 0x22, 0x33, 0x44), allowAlpha = true)
        assertEquals("#11223344", (withAlpha.write() as JsonPrimitive).asString)

        val noAlpha = ColorSetting("c", Color(0x11, 0x22, 0x33, 0x44), allowAlpha = false)
        assertEquals("#112233", (noAlpha.write() as JsonPrimitive).asString)
    }

    @Test
    fun `color round trips its base through hex`() {
        val s = ColorSetting("c", Color.BLACK, allowAlpha = true)
        s.value = Color(0xDE, 0xAD, 0xBE, 0xEF)
        val other = ColorSetting("c", Color.BLACK, allowAlpha = true)
        other.read(s.write())
        assertEquals(Color(0xDE, 0xAD, 0xBE, 0xEF), other.baseColor)
    }

    @Test
    fun `color reads a legacy raw int`() {
        val s = ColorSetting("c", Color.BLACK, allowAlpha = true)
        s.read(JsonPrimitive(Color(0x12, 0x34, 0x56, 0x78).rgb))
        assertEquals(Color(0x12, 0x34, 0x56, 0x78), s.baseColor)
    }

    @Test
    fun `color persists chroma parameters as an object`() {
        val s = ColorSetting("c", Color.RED, allowAlpha = true)
        s.value = Color(0x10, 0x20, 0x30, 0xFF)
        s.chroma = true
        s.chromaSpeed = 1.5f
        s.chromaMode = ChromaMode.PER_CHAR

        val obj = s.write() as JsonObject
        assertEquals("#102030FF", obj.get("color").asString)
        assertTrue(obj.get("chroma").asBoolean)
        assertEquals(1.5f, obj.get("chromaSpeed").asFloat)
        assertEquals("PER_CHAR", obj.get("chromaMode").asString)

        val other = ColorSetting("c", Color.RED, allowAlpha = true)
        other.read(obj)
        assertEquals(Color(0x10, 0x20, 0x30, 0xFF), other.baseColor)
        assertTrue(other.chroma)
        assertEquals(1.5f, other.chromaSpeed)
        assertEquals(ChromaMode.PER_CHAR, other.chromaMode)
    }

    @Test
    fun `color read tolerates junk and an unknown chroma mode`() {
        val s = ColorSetting("c", Color.RED, allowAlpha = true)
        s.read(JsonPrimitive("not-a-color"))
        assertEquals(Color.RED, s.baseColor) // base untouched

        val obj = JsonObject().apply {
            addProperty("color", "#0A0B0C")
            addProperty("chroma", true)
            addProperty("chromaMode", "SIDEWAYS") // not a ChromaMode
        }
        s.read(obj)
        assertEquals(Color(0x0A, 0x0B, 0x0C, 0xFF), s.baseColor)
        assertEquals(ChromaMode.ALL, s.chromaMode) // unknown mode left at default
    }

    @Test
    fun `keybind writes a typed object and reads it back`() {
        val s = KeybindSetting("k", default = Keys.F)
        s.bindMouse(1)
        val obj = s.write() as JsonObject
        assertEquals("MOUSE", obj.get("type").asString)
        assertEquals(1, obj.get("key").asInt)

        val other = KeybindSetting("k")
        other.read(obj)
        assertEquals(InputType.MOUSE, other.type)
        assertEquals(1, other.value)
        assertTrue(other.isBound)
    }

    @Test
    fun `keybind saves a keyboard key by Minecraft name and reads it back`() {
        val s = KeybindSetting("k", default = Keys.F)
        val obj = s.write() as JsonObject
        assertEquals("key.keyboard.f", obj.get("key").asString)

        val other = KeybindSetting("k")
        other.read(obj)
        assertEquals(Keys.F, other.value)
    }

    @Test
    fun `keybind reads the legacy bare int as a keyboard key`() {
        val s = KeybindSetting("k")
        s.read(JsonPrimitive(Keys.G))
        assertEquals(InputType.KEYBOARD, s.type)
        assertEquals(Keys.G, s.value)
    }

    @Test
    fun `keybind reset restores the type with the code`() {
        val mouse = KeybindSetting("k", default = 3, defaultType = InputType.MOUSE)
        mouse.bindKeyboard(Keys.F)
        mouse.reset()
        assertEquals(InputType.MOUSE, mouse.type)
        assertEquals(3, mouse.value)

        val key = KeybindSetting("k", default = Keys.F)
        key.bindMouse(1)
        key.reset()
        assertEquals(InputType.KEYBOARD, key.type)
        assertEquals(Keys.F, key.value)
    }

    @Test
    fun `keybind unbind clears to an unbound keyboard key`() {
        val s = KeybindSetting("k", default = Keys.F)
        assertTrue(s.isBound)
        s.unbind()
        assertEquals(InputType.KEYBOARD, s.type)
        assertFalse(s.isBound)
    }
}
