package org.blackaddons.core.backend.gateway

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.blackaddons.api.Platform
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.subscribe
import org.blackaddons.api.util.Uuids
import org.blackaddons.core.backend.gateway.Presence.WATCH_TTL_MS
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Who nearby has a BlackAddons socket. Absence proves nothing (live updates can be off). Subscribes the tab list's
 * UUIDs by diff. Subscriptions end with the socket. Read lock-free while drawing, because the set gets replaced whole.
 */
object Presence {

    /** Server limit. The server drops the excess silently. */
    private const val MAX_PER_FRAME = 100

    private const val MAX_SUBSCRIPTIONS = 200

    /** Longer than a reconcile tick. Expires soon after a screen closes. */
    private const val WATCH_TTL_MS = 30_000L

    /** The reconcile it feeds runs every 5 s. */
    private const val LOBBY_REFRESH_TICKS = 20

    @Volatile private var onlineNow: Set<UUID> = emptySet()

    /**
     * Published from the client tick, so the gateway timer (which also sends heartbeats) never waits on the client
     * thread.
     */
    @Volatile private var lobby: List<UUID> = emptyList()
    private var lobbyTicks = 0
    private val subscribed: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private val watched = ConcurrentHashMap<UUID, Long>()

    /** Lock-free, so safe while drawing. */
    fun isOnline(uuid: UUID?): Boolean = uuid != null && uuid in onlineNow

    val anyOnline: Boolean get() = onlineNow.isNotEmpty()

    /** Interest in a player outside the lobby (an open profile). Renewed each frame. Expires after [WATCH_TTL_MS]. */
    fun watch(uuid: UUID) {
        watched[uuid] = System.currentTimeMillis()
    }

    internal fun start() {
        EventBus.subscribe<ClientTickEvent.Post> {
            if (++lobbyTicks < LOBBY_REFRESH_TICKS) return@subscribe
            lobbyTicks = 0
            lobby = runCatching { Platform.world.onlinePlayerUuids }.getOrDefault(emptyList())
        }
    }

    internal fun reconcile() {
        // Watches win the cap. Remove duplicates first, so one player doesn't use two slots.
        val wanted = (liveWatches() + lobby).distinct().take(MAX_SUBSCRIPTIONS).toSet()

        val added = wanted - subscribed
        val gone = subscribed - wanted

        if (gone.isNotEmpty() && send(Op.PRESENCE_UNSUBSCRIBE, gone)) {
            subscribed.removeAll(gone)
            forget(gone)
        }
        if (added.isNotEmpty() && send(Op.PRESENCE_SUBSCRIBE, added)) {
            subscribed.addAll(added)
        }
    }

    /** A subscription answer or a transition, same shape. The HTTP listener and the gateway timer both write it. */
    @Synchronized
    internal fun onFrame(payload: JsonObject) {
        val players = payload.get("players")?.takeIf { it.isJsonArray }?.asJsonArray ?: return
        val next = HashSet(onlineNow)
        for (element in players) {
            val entry = element?.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val uuid = Uuids.parse(entry.stringOrNull("playerUuid")) ?: continue
            if (entry.get("online")?.takeIf { it.isJsonPrimitive }?.asBoolean == true) next.add(uuid) else next.remove(uuid)
        }
        onlineNow = next
    }

    @Synchronized
    internal fun onSocketLost() {
        subscribed.clear()
        onlineNow = emptySet()
        // Watches stay. The next socket subscribes them again.
    }

    private fun liveWatches(): List<UUID> {
        val cutoff = System.currentTimeMillis() - WATCH_TTL_MS
        watched.entries.removeIf { it.value < cutoff }
        return watched.keys.toList()
    }

    @Synchronized
    private fun forget(gone: Set<UUID>) {
        if (onlineNow.none { it in gone }) return
        onlineNow = onlineNow - gone
    }

    /** Sent in batches of the frame limit. False if any part was refused, so it retries next tick. */
    private fun send(op: String, uuids: Set<UUID>): Boolean {
        var allSent = true
        for (chunk in uuids.chunked(MAX_PER_FRAME)) {
            val payload = JsonObject().apply {
                add("uuids", JsonArray().apply { chunk.forEach { add(it.toString()) } })
            }
            if (!Gateway.say(op, payload)) allSent = false
        }
        return allSent
    }
}
