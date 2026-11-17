package org.blackaddons.api.util.concurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LatestOnlyTest {

    private val latest = LatestOnly()
    private val shown = mutableListOf<String>()

    @Test
    fun `an older answer landing last is dropped`() {
        val a = latest.next<String> { shown += it }
        val b = latest.next<String> { shown += it }
        b("B")
        a("A")
        assertEquals(listOf("B"), shown)
    }

    @Test
    fun `cancel drops what is in flight`() {
        val a = latest.next<String> { shown += it }
        latest.cancel()
        a("A")
        assertEquals(emptyList<String>(), shown)
    }
}
