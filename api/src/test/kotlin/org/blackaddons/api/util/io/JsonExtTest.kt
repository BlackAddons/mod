package org.blackaddons.api.util.io

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class JsonExtTest {

    @Test
    fun `an object parses`() = assertEquals("b", parseJsonObject("""{"a":"b"}""").str("a"))

    @Test
    fun `broken text, an array and a bare value are null`() {
        assertNull(parseJsonObject("{"))
        assertNull(parseJsonObject("[1]"))
        assertNull(parseJsonObject("42"))
        assertNull(parseJsonObject(""))
    }
}
