package org.blackaddons.core.backend.tickets

/** Ticket list and the open ticket. Fields get replaced whole. Writers lock, reads are lock-free. */
internal object TicketStore {

    @Volatile var tickets: List<TicketSummary> = emptyList()
        private set

    /** Tells "none" apart from "not yet". */
    @Volatile var loaded = false
        private set

    @Volatile var current: Ticket? = null
        private set

    /** Lines that arrive there need no notice. */
    @Volatile var viewing: Long? = null

    val unread: Int get() = tickets.sumOf { it.unread }

    @Synchronized
    fun onList(list: List<TicketSummary>) {
        tickets = list.sortedWith(compareByDescending<TicketSummary> { it.open }.thenByDescending { it.lastMessageAt })
        loaded = true
    }

    @Synchronized
    fun onTicket(ticket: Ticket) {
        current = ticket
        upsert(ticket.summary)
    }

    @Synchronized
    fun onMessage(summary: TicketSummary, message: TicketMessage, mine: Boolean) {
        val open = current
        if (open != null && open.summary.id == summary.id && open.messages.none { it.id == message.id }) {
            current = Ticket(summary, open.messages + message)
        }
        val known = tickets.firstOrNull { it.id == summary.id }
        val reading = viewing == summary.id
        upsert(summary.copy(unread = if (mine || reading) 0 else (known?.unread ?: 0) + 1))
    }

    @Synchronized
    fun onUpdated(summary: TicketSummary) {
        val known = tickets.firstOrNull { it.id == summary.id }
        upsert(summary.copy(unread = known?.unread ?: summary.unread))
        current?.takeIf { it.summary.id == summary.id }?.let { current = it.copy(summary = summary) }
    }

    @Synchronized
    fun onRemoved(id: Long) {
        tickets = tickets.filterNot { it.id == id }
        if (current?.summary?.id == id) current = null
    }

    @Synchronized
    fun onRead(id: Long) {
        tickets = tickets.map { if (it.id == id) it.copy(unread = 0) else it }
    }

    @Synchronized
    fun clear() {
        tickets = emptyList()
        loaded = false
        current = null
    }

    private fun upsert(summary: TicketSummary) {
        onList(tickets.filterNot { it.id == summary.id } + summary)
    }
}
