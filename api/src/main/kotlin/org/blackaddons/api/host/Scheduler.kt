package org.blackaddons.api.host

import org.blackaddons.api.host.Scheduler.Companion.MAX_FAILURES


/**
 * Shared client-tick scheduler; callbacks run on the client thread. Out-of-range tick counts throw. Failing
 * tasks are logged; repeating ones are dropped after [MAX_FAILURES] throws in a row.
 */
interface Scheduler {
    fun schedule(ticks: Int, action: () -> Unit): Cancellation
    fun every(ticks: Int, action: () -> Unit): Cancellation

    companion object {
        /** Same cap as EventBus listeners. */
        const val MAX_FAILURES = 5
    }
}

/** [cancel] is idempotent. */
interface Cancellation {
    fun cancel()
}
