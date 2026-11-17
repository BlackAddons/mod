package org.blackaddons.core.interaction

import org.blackaddons.api.feature.Feature
import org.blackaddons.api.player.ClickRequest
import org.blackaddons.api.player.ClientAction
import org.blackaddons.api.player.ClientActionResult
import org.blackaddons.api.world.BlockPos3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClickQueueTest {
    private val button = ClientAction.UseOn(BlockPos3(1, 2, 3))
    private val sent = mutableListOf<ClientAction>()
    private fun send(action: ClientAction): ClientActionResult {
        sent += action
        return ClientActionResult.SENT
    }

    @Test
    fun `nothing leaves before the click slot`() {
        val queue = ClickQueue(emptyList())
        val request = queue.submit(ClientAction.Use, null) {}
        queue.tick()
        assertTrue(sent.isEmpty())
        assertTrue(request.isPending)

        queue.drain(::send)
        assertEquals(listOf<ClientAction>(ClientAction.Use), sent)
        assertEquals(ClientActionResult.SENT, request.result)
    }

    @Test
    fun `no result handler runs while the queue is locked`() {
        val queue = ClickQueue(emptyList(), timeoutTicks = 0)
        val owner = Any()
        val lockedDuring = mutableListOf<Boolean>()
        fun watch(): (ClientActionResult) -> Unit = { lockedDuring += Thread.holdsLock(queue) }

        queue.submit(ClientAction.Use, owner, watch())
        queue.submit(ClientAction.Use, owner, watch()) // replaces the first
        queue.cancel(owner)
        queue.submit(ClientAction.Use, null, watch())
        queue.tick() // expires it
        queue.submit(ClientAction.Use, null, watch())
        queue.clear()

        assertEquals(listOf(false, false, false, false), lockedDuring)
    }

    @Test
    fun `requests go out in the order they were made`() {
        val queue = ClickQueue(emptyList())
        queue.submit(ClientAction.Attack, null) {}
        queue.submit(button, null) {}
        queue.submit(ClientAction.Use, null) {}
        queue.drain(::send)
        assertEquals(listOf(ClientAction.Attack, button, ClientAction.Use), sent)
    }

    @Test
    fun `a waiting request stays queued and does not hold up the others`() {
        var ready = false
        val queue = ClickQueue(listOf(ClickRule { action, _ ->
            if (action is ClientAction.UseOn && !ready) ClickVerdict.WAIT else ClickVerdict.GO
        }))
        val targeted = queue.submit(button, "a") {}
        queue.submit(ClientAction.Use, "b") {}

        queue.drain(::send)
        assertEquals(listOf<ClientAction>(ClientAction.Use), sent)
        assertTrue(targeted.isPending)

        ready = true
        queue.drain(::send)
        assertEquals(listOf(ClientAction.Use, button), sent)
        assertEquals(ClientActionResult.SENT, targeted.result)
    }

    @Test
    fun `a rejecting rule settles the request as blocked without sending`() {
        val queue = ClickQueue(listOf(ClickRule { _, _ -> ClickVerdict.REJECT }))
        val request = queue.submit(ClientAction.Use, null) {}
        queue.drain(::send)
        assertTrue(sent.isEmpty())
        assertEquals(ClientActionResult.BLOCKED, request.result)
    }

    @Test
    fun `a new request from the same owner replaces the pending one`() {
        val queue = ClickQueue(emptyList())
        val results = mutableListOf<ClientActionResult>()
        val first = queue.submit(ClientAction.Attack, "owner") { results += it }
        val second = queue.submit(button, "owner") { results += it }
        queue.submit(ClientAction.Use, null) {}

        assertEquals(ClientActionResult.CANCELLED, first.result)
        queue.drain(::send)
        assertEquals(listOf(button, ClientAction.Use), sent)
        assertEquals(ClientActionResult.SENT, second.result)
        assertEquals(listOf(ClientActionResult.CANCELLED, ClientActionResult.SENT), results)
    }

    @Test
    fun `anonymous requests never replace each other`() {
        val queue = ClickQueue(emptyList())
        queue.submit(ClientAction.Attack, null) {}
        queue.submit(ClientAction.Use, null) {}
        queue.drain(::send)
        assertEquals(listOf(ClientAction.Attack, ClientAction.Use), sent)
    }

    @Test
    fun `a request from a feature switched off since is cancelled, not sent`() {
        val feature = Switchable()
        feature.enabled = true
        val queue = ClickQueue(emptyList())
        val request = queue.submit(ClientAction.Use, feature) {}
        feature.enabled = false
        queue.drain(::send)
        assertTrue(sent.isEmpty())
        assertEquals(ClientActionResult.CANCELLED, request.result)
    }

    @Test
    fun `a request that waits too long expires`() {
        val queue = ClickQueue(listOf(ClickRule { _, _ -> ClickVerdict.WAIT }), timeoutTicks = 2)
        val request = queue.submit(button, null) {}
        repeat(2) {
            queue.tick()
            queue.drain(::send)
        }
        assertTrue(request.isPending)
        queue.tick()
        assertEquals(ClientActionResult.EXPIRED, request.result)
    }

    @Test
    fun `cancel and clear settle only what is still pending`() {
        val queue = ClickQueue(emptyList())
        val mine = queue.submit(ClientAction.Use, "mine") {}
        val other = queue.submit(ClientAction.Attack, "other") {}
        queue.cancel("mine")
        assertEquals(ClientActionResult.CANCELLED, mine.result)
        assertTrue(other.isPending)

        queue.clear(ClientActionResult.EXPIRED)
        assertEquals(ClientActionResult.EXPIRED, other.result)
        queue.drain(::send)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `a request cancelled by its handle never goes out`() {
        val queue = ClickQueue(emptyList())
        val request = queue.submit(ClientAction.Use, null) {}
        request.cancel()
        request.cancel()
        queue.drain(::send)
        assertTrue(sent.isEmpty())
        assertEquals(ClientActionResult.CANCELLED, request.result)
    }

    @Test
    fun `a result handler may queue the next click, which waits for the next slot`() {
        val queue = ClickQueue(emptyList())
        var next: ClickRequest? = null
        queue.submit(ClientAction.Use, null) { next = queue.submit(ClientAction.Attack, null) {} }
        queue.drain(::send)
        assertEquals(listOf<ClientAction>(ClientAction.Use), sent)
        assertTrue(next!!.isPending)

        queue.drain(::send)
        assertEquals(listOf(ClientAction.Use, ClientAction.Attack), sent)
    }

    @Test
    fun `a failing result handler does not stop the drain`() {
        val queue = ClickQueue(emptyList())
        queue.submit(ClientAction.Use, null) { error("boom") }
        val second = queue.submit(ClientAction.Attack, null) {}
        queue.drain(::send)
        assertEquals(ClientActionResult.SENT, second.result)
        assertFalse(second.isPending)
    }
}

// Named, because a feature id is its class name, and anonymous objects have none.
private class Switchable : Feature(name = "Switchable")
