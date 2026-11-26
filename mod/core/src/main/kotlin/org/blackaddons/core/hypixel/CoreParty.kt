package org.blackaddons.core.hypixel

import org.blackaddons.api.Platform
import org.blackaddons.api.chat.ChatChannel
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.event.chat.ChatReceiveEvent
import org.blackaddons.api.event.hypixel.PartyChangeEvent
import org.blackaddons.api.event.network.ServerLeaveEvent
import org.blackaddons.api.event.subscribe
import org.blackaddons.api.host.AccountLookup
import org.blackaddons.api.host.Cancellation
import org.blackaddons.api.hypixel.PartyMember
import org.blackaddons.api.hypixel.PartyProvider
import org.blackaddons.api.hypixel.PartyRole
import org.blackaddons.api.util.inWholeTicks
import org.blackaddons.core.log.Log
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** How a party request reaches the server. [send] says whether the request went out. */
class PartyTransport(val source: String, val send: () -> Boolean)

/**
 * The party as the server lists it. The server never pushes a change, so a party notice in chat (someone joined, left,
 * got promoted, the party was disbanded) triggers a new request. Client thread.
 */
object CoreParty : PartyProvider {

    /** Set by the platform. */
    var transport: PartyTransport? = null

    override var members: List<PartyMember> = emptyList()
        private set

    internal val diagnostics = PartyDiagnostics()

    private var queued: Cancellation? = null
    private var notBefore = TimeSource.Monotonic.markNow()

    fun start() {
        EventBus.subscribe<ChatReceiveEvent> {
            if (event.channel == ChatChannel.PARTY && event.playerMessage == null) ask("chat: ${event.unformatted}")
        }
        // A server hop covers parties joined from outside chat (the party finder, a warp).
        EventBus.subscribe<WorldChangeEvent> { ask("world change") }
        EventBus.subscribe<ServerLeaveEvent> { disconnected() }
    }

    internal fun disconnected() {
        queued?.cancel()
        queued = null
        update(emptyList())
    }

    override fun refresh() = ask("Party.refresh()")

    // Notices come in bursts (a disband is several lines), and Hypixel rate-limits the request: one per burst.
    internal fun ask(reason: String) {
        diagnostics.trigger = reason
        diagnostics.triggeredAt = TimeSource.Monotonic.markNow()
        if (queued != null) return
        val wait = (notBefore - TimeSource.Monotonic.markNow()).coerceAtLeast(Duration.ZERO)
        queued = Platform.scheduler.schedule(BURST_TICKS + wait.inWholeTicks.toInt()) {
            queued = null
            val sent = transport?.send?.invoke() == true
            diagnostics.sent(sent)
            if (sent) notBefore = TimeSource.Monotonic.markNow() + MIN_GAP
        }
    }

    internal val isQueued: Boolean get() = queued != null

    /** The server's answer: every member's role, or an empty map outside a party. */
    fun answered(roles: Map<UUID, PartyRole>) {
        diagnostics.answered()
        update(roles.map { (uuid, role) -> PartyMember(uuid, role, nameOf(uuid)) }.sortedBy { it.role })
        for (member in members) if (member.name == null) resolve(member.uuid)
    }

    /** The server refused. [reason] is its error, for the debugger. */
    fun refused(reason: String, rateLimited: Boolean) {
        diagnostics.refused(reason)
        if (!rateLimited) return
        Log.main.debug("Party request rate-limited, asking again later")
        notBefore = TimeSource.Monotonic.markNow() + RATE_LIMIT_BACKOFF
        ask("retry after rate limit")
    }

    private fun nameOf(uuid: UUID): String? = members.firstOrNull { it.uuid == uuid }?.name

    private fun resolve(uuid: UUID) = Platform.mojang.byUuid(uuid) { result ->
        if (result !is AccountLookup.Found) return@byUuid
        update(members.map { if (it.uuid == uuid) it.copy(name = result.name) else it })
    }

    private fun update(next: List<PartyMember>) {
        if (next == members) return
        members = next
        EventBus.post(PartyChangeEvent(next))
    }

    private const val BURST_TICKS = 10
    private val MIN_GAP = 1.seconds
    private val RATE_LIMIT_BACKOFF = 5.seconds
}

/** What the Party Debugger shows: the last trigger, request and answer, and how many of each. */
internal class PartyDiagnostics {
    var trigger: String? = null
    var triggeredAt: TimeSource.Monotonic.ValueTimeMark? = null
    var sentAt: TimeSource.Monotonic.ValueTimeMark? = null
        private set
    var lastSendWentOut = true
        private set
    var answeredAt: TimeSource.Monotonic.ValueTimeMark? = null
        private set
    var error: String? = null
        private set
    var requests = 0
        private set
    var answers = 0
        private set
    var refusals = 0
        private set

    fun sent(wentOut: Boolean) {
        lastSendWentOut = wentOut
        if (!wentOut) return
        sentAt = TimeSource.Monotonic.markNow()
        requests++
    }

    fun answered() {
        answeredAt = TimeSource.Monotonic.markNow()
        answers++
        error = null
    }

    fun refused(reason: String) {
        answeredAt = TimeSource.Monotonic.markNow()
        refusals++
        error = reason
    }
}
