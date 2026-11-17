package org.blackaddons.core.scheduler

import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.subscribe
import org.blackaddons.api.host.Cancellation
import org.blackaddons.api.host.Scheduler
import org.blackaddons.core.log.Log
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * [Scheduler] on one `ClientTickEvent.Post` listener. Client thread, FIFO per tick. Repeating tasks stop after
 * [Scheduler.MAX_FAILURES] throws in a row, so a broken task can't spam 20 stack traces a second.
 */
object DefaultScheduler : Scheduler {

    private val tasks = ConcurrentLinkedQueue<Task>()

    @Volatile private var tick: Long = 0L
    @Volatile private var started: Boolean = false

    fun startTicking() {
        if (started) return
        started = true
        EventBus.subscribe<ClientTickEvent.Post> {
            tick++
            val now = tick
            val iter = tasks.iterator()
            while (iter.hasNext()) {
                val t = iter.next()
                if (t.cancelled) {
                    iter.remove()
                    continue
                }
                if (now < t.fireAt) continue
                try {
                    t.action()
                    t.failures = 0
                } catch (th: Throwable) {
                    Log.main.error("Scheduler task threw", th)
                    if (++t.failures >= Scheduler.MAX_FAILURES) {
                        Log.main.warn("Dropping a repeating scheduler task after {} throws in a row", t.failures)
                        t.cancel()
                    }
                }
                if (t.everyTicks > 0 && !t.cancelled) {
                    t.fireAt = now + t.everyTicks
                } else {
                    iter.remove()
                }
            }
        }.register()
    }

    override fun schedule(ticks: Int, action: () -> Unit): Cancellation {
        require(ticks >= 0) { "ticks must be >= 0, got $ticks" }
        val task = Task(fireAt = tick + ticks, everyTicks = 0, action = action)
        tasks += task
        return task
    }

    override fun every(ticks: Int, action: () -> Unit): Cancellation {
        require(ticks > 0) { "ticks must be > 0 for repeating, got $ticks" }
        val task = Task(fireAt = tick + ticks, everyTicks = ticks, action = action)
        tasks += task
        return task
    }

    private class Task(
        var fireAt: Long,
        val everyTicks: Int,
        val action: () -> Unit,
    ) : Cancellation {
        @Volatile var cancelled: Boolean = false
        var failures = 0
        override fun cancel() { cancelled = true }
    }
}
