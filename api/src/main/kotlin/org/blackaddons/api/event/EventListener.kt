package org.blackaddons.api.event

class EventListener<T : Event>(
    val eventClass: Class<T>,
    val priority: EventPriority,
    val callback: EventBus.EventContext<T>.() -> Unit,
) {
    internal var consecutiveFailures: Int = 0

    fun register() = EventBus.register(this)
    fun unregister() = EventBus.unregister(this)
}
