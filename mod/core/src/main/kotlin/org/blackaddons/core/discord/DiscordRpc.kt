package org.blackaddons.core.discord

import org.blackaddons.api.util.concurrent.Workers
import org.blackaddons.core.discord.DiscordRpc.show
import org.blackaddons.core.log.Log
import java.util.UUID
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * Keeps the Discord activity in sync with [show] on its own worker. Discord not running is the normal case: it retries
 * quietly with a backoff and only logs at debug level.
 */
internal object DiscordRpc {

    /** The BlackAddons application, shared with the bot. Discord shows its name as "Playing ...". */
    private const val CLIENT_ID = "1537145376329564280"

    private const val TICK_MS = 1_000L

    // Discord allows 5 activity updates per 20 s. One update every 5 s stays under that, with room for a reconnect.
    private const val MIN_UPDATE_MS = 5_000L
    private const val FIRST_RETRY_MS = 5_000L
    private const val MAX_RETRY_MS = 60_000L

    @Volatile
    private var wanted: DiscordActivity? = null

    private var running: Pair<ScheduledExecutorService, Session>? = null

    /** The latest call wins. Null clears the activity. */
    fun show(activity: DiscordActivity?) {
        wanted = activity
    }

    @Synchronized
    fun start() {
        if (running != null) return
        val session = Session()
        val worker = Workers.scheduled("discord-rpc")
        worker.scheduleWithFixedDelay(session::tick, 0, TICK_MS, TimeUnit.MILLISECONDS)
        running = worker to session
    }

    /** Clears the activity before disconnecting, so Discord doesn't keep showing it. */
    @Synchronized
    fun stop() {
        val (worker, session) = running ?: return
        running = null
        wanted = null
        // Runs after any tick in progress, and then shutdown() stops rescheduling the periodic one.
        worker.execute(session::close)
        worker.shutdown()
    }

    /**
     * The state belongs to one start() call, so a restart gets a fresh connection instead of sharing one with a dying
     * worker.
     */
    private class Session {
        private var ipc: DiscordIpc? = null
        private var sent: DiscordActivity? = null
        private var synced = false
        private var lastSendAt = 0L
        private var nextConnectAt = 0L
        private var retryMs = FIRST_RETRY_MS
        private var lastError: String? = null

        fun tick() {
            val now = System.currentTimeMillis()
            try {
                val connection = ipc ?: connect(now) ?: return
                val target = wanted
                if (synced && target == sent) return
                if (now - lastSendAt < MIN_UPDATE_MS) return
                lastSendAt = now
                val reply = connection.send(setActivity(target))
                sent = target
                synced = true
                reportError(reply.get("evt")?.takeIf { it.isJsonPrimitive }?.asString, reply)
            } catch (t: Throwable) {
                Log.main.debug("Discord RPC dropped: {}", t.toString())
                ipc?.close()
                ipc = null
                synced = false
                nextConnectAt = now + retryMs
            }
        }

        fun close() {
            val connection = ipc ?: return
            runCatching { connection.send(setActivity(null)) }
            connection.close()
            ipc = null
        }

        private fun connect(now: Long): DiscordIpc? {
            if (now < nextConnectAt) return null
            val opened = DiscordIpc.open()
            if (opened == null) {
                nextConnectAt = now + retryMs
                retryMs = min(retryMs * 2, MAX_RETRY_MS)
                return null
            }
            val ready = runCatching { opened.send(mapOf("v" to 1, "client_id" to CLIENT_ID), DiscordIpc.OP_HANDSHAKE) }
                .getOrNull()?.get("evt")?.takeIf { it.isJsonPrimitive }?.asString
            if (ready != "READY") {
                opened.close()
                nextConnectAt = now + retryMs
                return null
            }
            Log.main.debug("Discord RPC connected")
            retryMs = FIRST_RETRY_MS
            ipc = opened
            return opened
        }

        /**
         * A rejected field rejects the whole activity every time, so each distinct reason gets logged once, not on
         * every update.
         */
        private fun reportError(event: String?, reply: Any) {
            if (event != "ERROR") return
            val message = reply.toString()
            if (message == lastError) return
            lastError = message
            Log.main.warn("Discord refused the activity: {}", message)
        }

        private fun setActivity(activity: DiscordActivity?) = mapOf(
            "cmd" to "SET_ACTIVITY",
            "args" to mapOf("pid" to ProcessHandle.current().pid(), "activity" to activity),
            "nonce" to UUID.randomUUID().toString(),
        )
    }
}
