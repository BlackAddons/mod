package org.blackaddons.core.backend.gateway

import com.google.gson.JsonObject
import org.blackaddons.api.Platform
import org.blackaddons.core.backend.Backend
import org.blackaddons.core.backend.Http
import org.blackaddons.core.backend.NetLog
import org.blackaddons.core.backend.gateway.GatewayConnection.Companion.SILENCE_FACTOR
import org.blackaddons.core.log.Log
import org.blackaddons.core.update.ReleaseChannel
import java.net.URI
import java.net.http.WebSocket
import java.net.http.WebSocketHandshakeException
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Why a connection ended. All of them mean "come back" except [Unsupported]. */
internal sealed interface GatewayOutcome {

    data object Closed : GatewayOutcome
    data class Handover(val withinSeconds: Int) : GatewayOutcome
    data object Unsupported : GatewayOutcome

    /** Drop the token, so the next attempt proves ownership again. */
    data object BadToken : GatewayOutcome

    /** Switched off on the server. That's settled, so wait long. */
    data object Off : GatewayOutcome

    /**
     * Temporarily unable, with [retryAfterSeconds] if given. Separate from [Off], because rolling deploys send this for
     * a few seconds. When we treated it as off, the mod slept for ten minutes on every release.
     */
    data class Unavailable(val retryAfterSeconds: Int?) : GatewayOutcome
}

internal interface GatewayHandler {

    /** `READY` verbatim: `playerUuid`, `accountId`, `cosmeticsVersion`, `mute`. */
    fun onReady(payload: JsonObject) {}

    fun onFrame(op: String, payload: JsonObject) {}
}

/**
 * One socket attempt from `HELLO` to its end. Keeps the connection alive (identify, heartbeat, silence detection) and
 * hands everything else to a [GatewayHandler]. [Gateway] owns the reconnect policy. Frames and timers run on different
 * threads, so shared state is volatile and [finish] runs once.
 */
internal class GatewayConnection(
    val token: String,
    private val handler: GatewayHandler,
    private val timers: ScheduledExecutorService,
    private val onEnd: (GatewayConnection, GatewayOutcome) -> Unit,
) {

    private val ended = AtomicBoolean(false)

    @Volatile private var socket: WebSocket? = null
    @Volatile private var beat: ScheduledFuture<*>? = null

    /**
     * Gives up on an attempt that never identifies (e.g. a proxy that accepts with nothing behind it). The silence
     * check only starts after `HELLO`.
     */
    @Volatile private var arrival: ScheduledFuture<*>? = null

    @Volatile private var lastFrameAt = TimeSource.Monotonic.markNow()

    @Volatile private var pending: GatewayOutcome? = null

    /** Chained, not locked: the client forbids overlapping sends, but allows queued sends. */
    private var sending: CompletableFuture<WebSocket> = CompletableFuture.completedFuture(null)

    @Volatile var established = false
        private set

    fun open() {
        // Armed before the handshake, because nothing else limits the wait for `READY`.
        arrival = timers.schedule(::neverArrived, ARRIVAL_SECONDS, TimeUnit.SECONDS)
        Http.webSocketBuilder()
            // The connect timeout only covers TCP. An unanswered upgrade would never complete.
            .connectTimeout(Duration.ofSeconds(HANDSHAKE_SECONDS))
            .buildAsync(URI.create(Backend.GATEWAY_URL), Listener())
            .whenComplete { ws, error -> if (error != null) failedHandshake(error) else adopt(ws) }
    }

    fun say(frame: Frame): Boolean {
        if (!established || ended.get()) return false
        send(frame)
        return true
    }

    /** No goodbye, because the far end might not be listening. */
    fun abort() {
        NetLog.trace("Gateway aborting the socket")
        drop()
    }

    fun close() {
        if (ended.get()) return
        finish(GatewayOutcome.Closed)
        socket?.let { runCatching { it.sendClose(WebSocket.NORMAL_CLOSURE, "bye") } }
    }

    // End first, socket second (same as adopt()), so a handshake that finishes after the end always gets aborted.
    private fun drop() {
        finish(GatewayOutcome.Closed)
        socket?.abort()
    }

    private fun adopt(ws: WebSocket) {
        socket = ws
        if (ended.get()) ws.abort()
    }

    private fun failedHandshake(error: Throwable) {
        val handshake = generateSequence(error) { it.cause }.filterIsInstance<WebSocketHandshakeException>().firstOrNull()
        val response = handshake?.response
        NetLog.trace("Gateway handshake failed: status={}, error={}", response?.statusCode(), error.toString())
        when (response?.statusCode()) {
            404 -> finish(GatewayOutcome.Off)
            503 -> finish(GatewayOutcome.Unavailable(response.headers().firstValue("Retry-After").orElse(null)?.toIntOrNull()))
            401, 403 -> finish(GatewayOutcome.BadToken)
            else -> {
                finish(GatewayOutcome.Closed)
            }
        }
    }

    private fun onFrame(frame: Frame) {
        lastFrameAt = TimeSource.Monotonic.markNow()
        if (frame.op != Op.HEARTBEAT_ACK) NetLog.trace("Gateway frame {}", frame.op)
        when (frame.op) {
            Op.HELLO -> identify(frame.d)
            Op.READY -> {
                established = true
                arrival?.cancel(false)
                arrival = null
                handler.onReady(frame.d)
            }
            Op.HEARTBEAT_ACK -> handler.onFrame(frame.op, frame.d)
            Op.RECONNECT -> pending = GatewayOutcome.Handover(frame.d.intOrNull("withinSeconds") ?: DEFAULT_HANDOVER_SECONDS)
            Op.ERROR -> {
                NetLog.trace("Gateway error frame: {}", frame.d)
                pending = verdict(frame.d)
            }
            else -> handler.onFrame(frame.op, frame.d)
        }
    }

    /** The heartbeat interval always comes from the server. It's how the server spreads load. */
    private fun identify(hello: JsonObject) {
        NetLog.trace("Gateway identifying as {} ({}, MC {})", Platform.blackaddonsVersion, ReleaseChannel.current, Platform.minecraftVersion)
        send(Frame(Op.IDENTIFY, JsonObject().apply {
            addProperty("token", token)
            addProperty("version", Platform.blackaddonsVersion)
            // Sent so the server judges `unsupported` against our own release track.
            addProperty("channel", ReleaseChannel.current)
            addProperty("mcVersion", Platform.minecraftVersion)
        }))

        val seconds = (hello.intOrNull("heartbeatIntervalSeconds") ?: DEFAULT_HEARTBEAT_SECONDS)
            .coerceAtLeast(MIN_HEARTBEAT_SECONDS).toLong()
        beat = timers.scheduleAtFixedRate({ pulse(seconds) }, seconds, seconds, TimeUnit.SECONDS)
        // finish() might have run in between.
        if (ended.get()) beat?.cancel(false)
    }

    /** Silence past [SILENCE_FACTOR] intervals means a dead socket (dropped route, sleeping laptop). */
    private fun pulse(intervalSeconds: Long) {
        val silentFor = lastFrameAt.elapsedNow()
        if (silentFor > (intervalSeconds * SILENCE_FACTOR).seconds) {
            NetLog.trace("Gateway went silent for {}s - dropping the socket", silentFor.inWholeSeconds)
            drop()
            return
        }
        send(Frame(Op.HEARTBEAT, JsonObject()))
    }

    private fun neverArrived() {
        if (ended.get()) return
        NetLog.trace("Gateway did not identify within {}s - dropping the attempt", ARRIVAL_SECONDS)
        drop()
    }

    private fun verdict(payload: JsonObject): GatewayOutcome = when (payload.stringOrNull("code")) {
        GatewayError.VERSION_UNSUPPORTED -> GatewayOutcome.Unsupported
        GatewayError.INVALID_TOKEN -> GatewayOutcome.BadToken
        else -> GatewayOutcome.Closed
    }

    private fun send(frame: Frame) {
        val ws = socket ?: return
        val text = frame.encode()
        synchronized(this) {
            // Chained, not awaited: a blocked timer thread would stall its other timers.
            sending = sending.handle { _, _ -> null }.thenCompose { ws.sendText(text, true) }
        }
    }

    private fun finish(outcome: GatewayOutcome) {
        if (!ended.compareAndSet(false, true)) return
        beat?.cancel(false)
        beat = null
        arrival?.cancel(false)
        arrival = null
        // Passes itself, so a late callback can't clear its replacement.
        onEnd(this, outcome)
    }

    private inner class Listener : WebSocket.Listener {

        private val partial = StringBuilder()

        override fun onOpen(webSocket: WebSocket) {
            NetLog.trace("Gateway socket open")
            adopt(webSocket)
            webSocket.request(1)
        }

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            if (ended.get()) return null
            partial.append(data)
            if (last) {
                val text = partial.toString()
                partial.setLength(0)
                Frame.decode(text)?.let { runCatching { onFrame(it) }.onFailure { t -> Log.main.warn("Gateway frame handling failed", t) } }
            }
            webSocket.request(1)
            return null
        }

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
            NetLog.trace("Gateway socket closed by the server: {} {}", statusCode, reason)
            finish(pending ?: GatewayOutcome.Closed)
            return null
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            NetLog.trace("Gateway socket error: {}", error.toString())
            finish(pending ?: GatewayOutcome.Closed)
        }
    }

    private companion object {
        /** Only if `HELLO` omits it, which the contract says never happens. */
        const val DEFAULT_HEARTBEAT_SECONDS = 30

        /** Minimum, so a bad server value can't turn clients into a hot loop. */
        const val MIN_HEARTBEAT_SECONDS = 5

        /** Matches the server's own silence threshold. */
        const val SILENCE_FACTOR = 2.5

        const val DEFAULT_HANDOVER_SECONDS = 30

        /** Without it, the upgrade request has no deadline. */
        const val HANDSHAKE_SECONDS = 15L

        /** Longer than the handshake plus the server's identify window. */
        const val ARRIVAL_SECONDS = 30L
    }
}
