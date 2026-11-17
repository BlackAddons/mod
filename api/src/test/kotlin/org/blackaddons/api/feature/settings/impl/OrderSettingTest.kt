package org.blackaddons.api.feature.settings.impl

import com.google.gson.JsonArray
import com.google.gson.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OrderSettingTest {

    private fun setting() = OrderSetting("Priority", listOf("Spirit", "Phoenix", "Bonzo"))

    @Test
    fun `dragging keeps every option exactly once`() {
        val order = setting()
        order.move(2, 0)
        assertEquals(listOf("Bonzo", "Spirit", "Phoenix"), order.value)
    }

    @Test
    fun `a saved order drops unknown entries and appends missing options`() {
        val order = setting()
        order.read(JsonArray().apply { listOf("Bonzo", "Typo", "Bonzo").forEach { add(JsonPrimitive(it)) } })
        assertEquals(listOf("Bonzo", "Spirit", "Phoenix"), order.value)
    }

    @Test
    fun `adding an entry outside the options changes nothing`() {
        val order = setting()
        order.add("Anything")
        assertEquals(listOf("Spirit", "Phoenix", "Bonzo"), order.value)
    }
}
