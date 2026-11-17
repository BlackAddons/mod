package org.blackaddons.api.util.concurrent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class LoadOnceTest {

    private val loads = AtomicInteger()

    private fun <V : Any> settle(loader: LoadOnce<String, V>, key: String) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (loader.isLoading(key)) {
            check(System.currentTimeMillis() < deadline) { "load of $key never finished" }
            Thread.onSpinWait()
        }
    }

    @Test
    fun `the first ask starts one load and later asks answer from memory`() {
        val release = CountDownLatch(1)
        val loader = LoadOnce<String, String>("test") { key ->
            loads.incrementAndGet()
            release.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            key.uppercase()
        }
        assertNull(loader["a"])
        assertTrue(loader.isLoading("a"))
        assertNull(loader["a"])
        release.countDown()
        settle(loader, "a")
        assertEquals("A", loader["a"])
        assertEquals(1, loads.get())
    }

    @Test
    fun `a failure is final by default`() {
        val loader = LoadOnce<String, String>("test") { loads.incrementAndGet(); null }
        loader["a"]
        settle(loader, "a")
        assertNull(loader["a"])
        assertFalse(loader.isLoading("a"))
        assertEquals(1, loads.get())
    }

    @Test
    fun `a failure is asked again once the retry delay passed`() {
        val loader = LoadOnce<String, String>("test", retryAfterMs = 0) {
            loads.incrementAndGet()
            error("offline")
        }
        loader["a"]
        settle(loader, "a")
        loader["a"]
        settle(loader, "a")
        assertEquals(2, loads.get())
    }

    @Test
    fun `onLoaded runs once per success`() {
        val seen = AtomicInteger()
        val loader = LoadOnce<String, String>("test", onLoaded = { _, _ -> seen.incrementAndGet() }) { it }
        loader["a"]
        settle(loader, "a")
        loader["a"]
        assertEquals(1, seen.get())
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
