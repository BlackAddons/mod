package org.blackaddons.api.event

import org.blackaddons.api.log.Log
import org.blackaddons.api.util.dev.DevFlags
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Listeners for a class also receive its subclasses' events, merged by priority. Listener exceptions are
 * logged (rethrown in dev or with `DevFlags.rethrowListenerExceptions`).
 */
object EventBus {

    data class EventContext<T : Event>(val event: T)

    private val listeners = ConcurrentHashMap<Class<*>, AtomicReference<List<EventListener<*>>>>()

    /**
     * Merged, sorted listener list per event class, so hot events skip the superclass walk. Any
     * (un)registration clears it all.
     */
    private val dispatchCache = ConcurrentHashMap<Class<*>, List<EventListener<*>>>()

    private const val MAX_CONSECUTIVE_FAILURES = 5

    fun <T : Event> register(listener: EventListener<T>) {
        val ref = listeners.computeIfAbsent(listener.eventClass) { AtomicReference(emptyList()) }
        while (true) {
            val current = ref.get()
            if (listener in current) return
            val next = (current + listener).sortedByDescending { it.priority.ordinal }
            if (ref.compareAndSet(current, next)) {
                dispatchCache.clear()
                return
            }
        }
    }

    fun <T : Event> unregister(listener: EventListener<T>) {
        val ref = listeners[listener.eventClass] ?: return
        while (true) {
            val current = ref.get()
            if (listener !in current) return
            val next = current - listener
            if (ref.compareAndSet(current, next)) {
                dispatchCache.clear()
                return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Event> post(event: T): T {
        val collected = dispatchCache.computeIfAbsent(event::class.java) { collectListeners(it) }
        if (collected.isEmpty()) return event

        if (DevFlags.logEventDispatch) {
            Log.main.info(
                "[dev] dispatch {} -> {} listener(s)",
                event::class.simpleName, collected.size,
            )
        }

        val ctx = EventContext(event)
        for (listener in collected) {
            try {
                (listener as EventListener<T>).callback(ctx)
                listener.consecutiveFailures = 0
            } catch (t: Throwable) {
                listener.consecutiveFailures++
                Log.main.error(
                    "Event listener for ${event::class.simpleName} threw (failure #${listener.consecutiveFailures})",
                    t,
                )
                if (DevFlags.isDevelopment || DevFlags.rethrowListenerExceptions) throw t
                if (listener.consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    Log.main.error(
                        "Auto-unregistering listener for ${event::class.simpleName} after $MAX_CONSECUTIVE_FAILURES consecutive failures",
                    )
                    listener.unregister()
                }
            }
            if (event is Cancellable && event.cancelled) break
        }
        return event
    }

    private fun collectListeners(eventClass: Class<*>): List<EventListener<*>> {
        var collected: MutableList<EventListener<*>>? = null
        var levels = 0
        var c: Class<*>? = eventClass
        while (c != null && Event::class.java.isAssignableFrom(c)) {
            val snapshot = listeners[c]?.get()
            if (!snapshot.isNullOrEmpty()) {
                if (collected == null) collected = ArrayList(snapshot.size)
                collected.addAll(snapshot)
                levels++
            }
            c = c.superclass
        }
        val all = collected ?: return emptyList()
        return if (levels <= 1) all else all.sortedByDescending { it.priority.ordinal }
    }
}

/**
 * Registers on [EventBus] for the whole mod lifetime. Inside a [org.blackaddons.api.feature.Feature] use its
 * `register` so enable/disable applies.
 */
inline fun <reified T : Event> EventBus.subscribe(
    priority: EventPriority = EventPriority.NORMAL,
    noinline block: EventBus.EventContext<T>.() -> Unit,
): EventListener<T> = EventListener(T::class.java, priority, block).apply { register() }
