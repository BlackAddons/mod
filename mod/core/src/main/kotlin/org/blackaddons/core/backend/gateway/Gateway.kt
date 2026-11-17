package org.blackaddons.core.backend.gateway

import com.google.gson.JsonObject
import org.blackaddons.api.util.concurrent.OffThread
import org.blackaddons.api.util.concurrent.Workers
import org.blackaddons.core.backend.Backend
import org.blackaddons.core.backend.BackendStatus
import org.blackaddons.core.backend.NetLog
import org.blackaddons.core.backend.auth.BackendSession
import org.blackaddons.core.backend.auth.HostMojangSession
import org.blackaddons.core.backend.chat.ChatStore
import org.blackaddons.core.backend.gateway.Gateway.connecting
import org.blackaddons.core.backend.gateway.Gateway.restart
import org.blackaddons.core.features.impl.settings.Connection
import org.blackaddons.core.log.Log
import org.blackaddons.core.soloclears.RunRecorder
import org.blackaddons.core.update.UpdateCheck
import java.util.UUID
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Keeps one backend socket open. It's an update channel (cosmetics, versions...). The player's switch ([Connection])
 * trades push for polling. A timer reconciles it instead of reacting to the switch, because the config loader writes
 * the switch at startup.
 */
object Gateway {

    private val timers: ScheduledExecutorService = Workers.scheduled("gateway")

    private val started = AtomicBoolean(false)
    private val backoff = GatewayBackoff()

    @Volatile private var connection: GatewayConnection? = null

    /**
     * Who the live socket identified as. IAS and the Auth addon swap `Minecraft.user` without closing anything, and
     * everything sent on the socket (chat included) goes out as this player.
     */
    @Volatile private var account: UUID? = null

    @Volatile private var connecting = false

    /** [restart] increments it, so a hung token request gets abandoned instead of holding [connecting] forever. */
    private val generation = AtomicInteger()

    @Volatile private var lastIdleReason: String? = null

    @Volatile var lastOutcome: String? = null
        private set

    @Volatile private var notBeforeNanos = 0L

    @Volatile private var blocked = false

    /** True when the socket is up and identified as the signed-in player. An unidentified socket delivers nothing. */
    val connected: Boolean get() = connection?.established == true && !accountChanged

    private val accountChanged: Boolean get() = account != HostMojangSession.session.uuid

    val unsupported: Boolean get() = blocked

    /**
     * Below the supported version, found from a refused `IDENTIFY` or from the update check (the minimum can rise while
     * a socket is open). Permanent. Reconcile closes the live socket.
     */
    internal fun blockUnsupported() {
        if (blocked) return
        blocked = true
        Log.main.warn("This build is below the supported version - live updates are off until it is updated")
        BackendStatus.onUnsupported()
        // The update check names the version. No loop, because the flag is already set.
        UpdateCheck.recheck()
    }

    // Refused until the socket reopens, so nothing goes out as the previous player in the meantime.
    internal fun say(op: String, payload: JsonObject): Boolean =
        !accountChanged && connection?.say(Frame(op, payload)) == true

    fun start() {
        if (!started.compareAndSet(false, true)) return
        Presence.start()
        timers.scheduleWithFixedDelay(::reconcile, 0, RECONCILE_SECONDS, TimeUnit.SECONDS)
    }

    /** Drops the socket, abandons any token fetch and clears backoff. For stuck states the supervisor can't see. */
    fun restart() {
        timers.execute {
            NetLog.trace("Gateway restart requested (connected={}, connecting={})", connected, connecting)
            reconnect()
        }
    }

    private fun reconnect() {
        generation.incrementAndGet()
        connecting = false
        // Ends synchronously, so [connection] is already cleared for the reconcile below.
        connection?.abort()
        // Runs after the abort, which sets its own backoff.
        backoff.reset()
        notBeforeNanos = 0L
        lastIdleReason = null
        reconcile()
    }

    // Skips pending backoff, but leaves live and handshaking attempts alone and never authenticates again.
    internal fun hurry() {
        timers.execute {
            if (connection != null || connecting) return@execute
            NetLog.trace("Gateway hurried: skipping the backoff")
            backoff.reset()
            notBeforeNanos = 0L
            lastIdleReason = null
            reconcile()
        }
    }

    fun describe(): String {
        val waitSeconds = TimeUnit.NANOSECONDS.toSeconds((notBeforeNanos - System.nanoTime()).coerceAtLeast(0L))
        return when {
            blocked -> "blocked: this build is below the supported version"
            !Connection.liveUpdates -> "off: Live Updates is disabled"
            connected -> "connected"
            connection != null -> "handshaking"
            connecting -> "proving ownership"
            waitSeconds > 0 -> "waiting ${waitSeconds}s before the next attempt"
            else -> "idle, connecting on the next tick"
        } + (lastOutcome?.let { " (last end: $it)" } ?: "")
    }

    private fun reconcile() {
        val wanted = Connection.liveUpdates && !blocked
        val current = connection

        if (!wanted) {
            idle(if (blocked) "blocked by version" else "live updates off")
            if (current != null) {
                NetLog.trace("Gateway no longer wanted - closing")
                // [ended] clears it, and close() calls that synchronously.
                current.close()
            }
            return
        }

        if (current != null) {
            if (accountChanged) {
                NetLog.trace("Gateway: the signed-in account changed - reconnecting as the new one")
                return reconnect()
            }
            Presence.reconcile()
            return
        }
        if (connecting) return idle("an attempt is already in flight")
        val wait = notBeforeNanos - System.nanoTime()
        if (wait > 0) return idle("backing off for ${TimeUnit.NANOSECONDS.toSeconds(wait) + 1}s")
        lastIdleReason = null
        connect()
    }

    private fun idle(reason: String) {
        if (reason == lastIdleReason) return
        lastIdleReason = reason
        NetLog.trace("Gateway not connecting: {}", reason)
    }

    /** Fetches the token off the timer thread, because it might need a challenge, Mojang and a login. */
    private fun connect() {
        connecting = true
        val attemptOf = generation.get()
        NetLog.trace("Gateway connecting: asking for a session token")
        OffThread.run(
            name = "gateway-connect",
            /*
             * Read the account before the token: if the player switches in between, it reads as a change and reconnects
             * once more.
             */
            work = { HostMojangSession.session.uuid to BackendSession.validToken() },
            onFailure = { null to null },
            // Back on the timer thread, where every state change happens.
            onResult = { (account, token) -> timers.execute { onToken(attemptOf, account, token) } },
        )
    }

    private fun onToken(attemptOf: Int, account: UUID?, token: String?) {
        if (attemptOf != generation.get()) {
            NetLog.trace("Gateway dropped a token that arrived after a restart")
            return
        }
        if (!Connection.liveUpdates || blocked) {
            NetLog.trace("Gateway dropped a token: no longer wanted")
            connecting = false
            return
        }
        NetLog.trace("Gateway token {}", if (token == null) "unavailable" else "ready")
        if (token == null) {
            connecting = false
            defer(backoff.nextDelaySeconds())
        } else {
            open(token, account)
        }
    }

    private fun open(token: String, account: UUID?) {
        NetLog.trace("Gateway opening socket to {}", Backend.GATEWAY_URL)
        val attempt = GatewayConnection(token, GatewayRouter, timers, ::ended)
        this.account = account
        connection = attempt
        connecting = false
        attempt.open()
    }

    /** Matched to the live attempt. Sockets close on three threads, and a stale callback would orphan the live one. */
    private fun ended(attempt: GatewayConnection, outcome: GatewayOutcome) {
        if (connection !== attempt) return
        val wasEstablished = attempt.established
        lastOutcome = outcome.toString()
        NetLog.trace("Gateway connection ended: {} (was identified: {})", outcome, wasEstablished)
        connection = null
        // The server keeps no subscriptions across sockets.
        Presence.onSocketLost()
        ChatStore.onSocketLost()
        // A run keeps its session (it's in Redis), but the commitment in flight has to be sent again.
        RunRecorder.onSocketLost()
        if (wasEstablished) backoff.reset()

        when (outcome) {
            is GatewayOutcome.Handover -> defer(backoff.withinWindowSeconds(outcome.withinSeconds))
            is GatewayOutcome.Unsupported -> blockUnsupported()

            is GatewayOutcome.BadToken -> {
                BackendSession.invalidate(attempt.token)
                defer(backoff.nextDelaySeconds())
            }

            is GatewayOutcome.Off -> defer(OFF_SECONDS)

            // A bare 503 is usually a rolling deploy, so the normal jittered backoff fits.
            is GatewayOutcome.Unavailable -> defer(
                outcome.retryAfterSeconds?.toLong()?.coerceAtLeast(1) ?: backoff.nextDelaySeconds(),
            )

            is GatewayOutcome.Closed -> defer(backoff.nextDelaySeconds())
        }
    }

    private fun defer(seconds: Long) {
        NetLog.trace("Gateway next attempt in {}s", seconds)
        notBeforeNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
    }

    private const val RECONCILE_SECONDS = 5L

    /** The transport was switched off, so fast retries don't help. */
    private const val OFF_SECONDS = 600L
}
