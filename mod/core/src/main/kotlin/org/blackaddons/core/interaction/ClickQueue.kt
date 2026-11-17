package org.blackaddons.core.interaction

import org.blackaddons.api.player.ClickRequest
import org.blackaddons.api.player.ClientAction
import org.blackaddons.api.player.ClientActionResult
import org.blackaddons.core.control.isSwitchedOff
import org.blackaddons.core.log.Log

/** [WAIT] keeps it queued. [REJECT] settles it as BLOCKED. */
enum class ClickVerdict { GO, WAIT, REJECT }

/** The first non-GO verdict wins. */
fun interface ClickRule {
    fun check(action: ClientAction, owner: Any?): ClickVerdict
}

/**
 * The one exit for clicks. The platform calls [drain] from the tick's click slot, where [rules] get checked, so a rule
 * applies to every feature. Requests go in order. A waiting request doesn't block others.
 */
class ClickQueue(
    private val rules: List<ClickRule>,
    private val timeoutTicks: Int = DEFAULT_TIMEOUT_TICKS,
) {
    // Callbacks run outside the lock: an onResult that waits on the client thread would deadlock against drain().
    private val pending = ArrayList<Request>()

    fun submit(action: ClientAction, owner: Any?, onResult: (ClientActionResult) -> Unit): ClickRequest {
        val request = Request(action, owner, onResult)
        val replaced = synchronized(this) {
            val replaced = owner?.let { o -> pending.filter { it.owner === o } }.orEmpty()
            pending.removeAll(replaced.toSet())
            pending += request
            replaced
        }
        replaced.forEach { it.settle(ClientActionResult.CANCELLED) }
        return request
    }

    fun cancel(owner: Any) = settleWhere(ClientActionResult.CANCELLED) { it.owner === owner }

    /** Cancelled on world change, expired behind a screen. */
    fun clear(result: ClientActionResult = ClientActionResult.CANCELLED) = settleWhere(result) { true }

    fun tick() {
        synchronized(this) { pending.forEach { it.age++ } }
        settleWhere(ClientActionResult.EXPIRED) { it.age > timeoutTicks }
    }

    /** From the click slot only. [send] answers SENT or BLOCKED. */
    fun drain(send: (ClientAction) -> ClientActionResult) {
        val settled = ArrayList<Pair<Request, ClientActionResult>>()
        synchronized(this) {
            val iterator = pending.iterator()
            while (iterator.hasNext()) {
                val request = iterator.next()
                val result = when {
                    isSwitchedOff(request.owner) -> ClientActionResult.CANCELLED
                    else -> when (verdict(request)) {
                        ClickVerdict.WAIT -> continue
                        ClickVerdict.REJECT -> ClientActionResult.BLOCKED
                        ClickVerdict.GO -> send(request.action)
                    }
                }
                iterator.remove()
                settled += request to result
            }
        }
        settled.forEach { (request, result) -> request.settle(result) }
    }

    private fun verdict(request: Request): ClickVerdict {
        for (rule in rules) {
            val verdict = rule.check(request.action, request.owner)
            if (verdict != ClickVerdict.GO) return verdict
        }
        return ClickVerdict.GO
    }

    private inline fun settleWhere(result: ClientActionResult, predicate: (Request) -> Boolean) {
        val gone = synchronized(this) {
            pending.filter(predicate).also { pending.removeAll(it.toSet()) }
        }
        gone.forEach { it.settle(result) }
    }

    private inner class Request(
        val action: ClientAction,
        val owner: Any?,
        private val onResult: (ClientActionResult) -> Unit,
    ) : ClickRequest {
        var age = 0

        @Volatile
        override var result: ClientActionResult? = null
            private set

        override fun cancel() {
            synchronized(this@ClickQueue) {
                if (!pending.remove(this)) return
            }
            settle(ClientActionResult.CANCELLED)
        }

        fun settle(outcome: ClientActionResult) {
            if (result != null) return
            result = outcome
            runCatching { onResult(outcome) }.onFailure { Log.main.error("Click result handler failed", it) }
        }
    }

    companion object {
        /** Long enough for a turn to reach the server. */
        const val DEFAULT_TIMEOUT_TICKS = 20
    }
}
