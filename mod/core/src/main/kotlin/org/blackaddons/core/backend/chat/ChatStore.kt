package org.blackaddons.core.backend.chat

import com.google.gson.JsonObject
import org.blackaddons.api.util.Uuids
import org.blackaddons.core.backend.Timestamps
import org.blackaddons.core.backend.chat.ChatStore.firstId
import org.blackaddons.core.backend.gateway.stringOrNull
import org.blackaddons.core.chat.ChatMirror
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal enum class Arrival {
    NEW,
    PICTURES_ADDED,
    REVISED,
    NOTHING,
}

/**
 * In-game chat state. Gateway threads write it, drawing reads it. Channels are immutable lists that get replaced whole.
 * Writes are synchronized, reads are lock-free. Sorted and deduplicated by id, because fan-out and backfill overlap
 * after reconnects.
 */
object ChatStore {

    /** From `READY`. The server still checks every send. */
    data class Mute(val reason: String?, val expiresAt: Long?)

    data class Refusal(val code: String, val detail: String?, val at: Long)

    private class Typist(val name: String, val until: Long)

    private val lines = ConcurrentHashMap<ChatChannel, List<ChatLine>>()
    private val typing = ConcurrentHashMap<ChatChannel, MutableMap<UUID, Typist>>()
    private val unread = ConcurrentHashMap<ChatChannel, Int>()
    private val backfilled = ConcurrentHashMap<ChatChannel, Boolean>()
    private val older = ConcurrentHashMap<ChatChannel, Boolean>()

    // Lines kept per channel. It grows as the reader pages back, so a new live line doesn't trim away what they loaded.
    private val caps = ConcurrentHashMap<ChatChannel, Int>()

    /** Picture metadata by hash, for places that only have the hash (game chat hover). LRU-bounded. */
    private val pictures = object : LinkedHashMap<String, ChatPicture>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, ChatPicture>): Boolean = size > MAX_PICTURES
    }

    @Volatile
    var mute: Mute? = null
        private set

    /** From `READY`, not from Minecraft, because network threads read it. */
    @Volatile
    var selfUuid: UUID? = null
        private set

    @Volatile
    var refusal: Refusal? = null
        private set

    /** Null while closed. Drives unread counts. */
    @Volatile
    var viewing: ChatChannel? = null
        private set

    fun lines(channel: ChatChannel): List<ChatLine> = lines[channel].orEmpty()

    fun lastId(channel: ChatChannel): Long? = lines[channel]?.lastOrNull()?.id

    fun firstId(channel: ChatChannel): Long? = lines[channel]?.firstOrNull()?.id

    /** True when the server has lines before [firstId] and there's room to keep them. */
    fun hasOlder(channel: ChatChannel): Boolean = older[channel] == true

    fun isBackfilled(channel: ChatChannel): Boolean = backfilled[channel] == true

    fun unread(channel: ChatChannel): Int = unread[channel] ?: 0

    fun picture(hash: String): ChatPicture? = synchronized(this) { pictures[hash] }

    fun typists(channel: ChatChannel): List<String> {
        val entries = typing[channel] ?: return emptyList()
        val now = System.currentTimeMillis()
        synchronized(this) {
            entries.values.removeIf { it.until < now }
            return entries.values.map { it.name }
        }
    }

    fun onViewing(channel: ChatChannel?) {
        viewing = channel
        if (channel != null) unread[channel] = 0
    }

    fun clearRefusal() {
        refusal = null
    }

    internal fun onRefused(code: String, detail: String?) {
        refusal = Refusal(code, detail, System.currentTimeMillis())
    }

    /** Duplicates after a reconnect must not echo again. An edit that adds a picture must. */
    internal fun onMessage(line: ChatLine): Arrival = insert(line, counted = true)

    /** Inserts the line if it's missing, because it can be older than our connection. */
    internal fun onEdit(line: ChatLine) {
        onMessage(line)
    }

    internal fun onDeleted(id: Long): Unit = synchronized(this) {
        for ((channel, held) in lines) {
            val at = held.binarySearchBy(id) { it.id }
            if (at >= 0) {
                lines[channel] = held.toMutableList().also { it.removeAt(at) }
                ChatMirror.showRetracted(id)
                return
            }
        }
    }

    /** [latest] means the newest page instead of a catch-up after the last id, so [hasMore] is about older lines. */
    internal fun onHistory(channel: ChatChannel, page: List<ChatLine>, hasMore: Boolean, latest: Boolean) {
        page.forEach { insert(it, counted = false) }
        backfilled[channel] = true
        if (latest) older[channel] = hasMore
    }

    internal fun onOlder(channel: ChatChannel, page: List<ChatLine>, hasMore: Boolean): Unit = synchronized(this) {
        val cap = (lines(channel).size + page.size).coerceIn(MAX_LINES, MAX_LOADED_LINES)
        caps[channel] = cap
        page.forEach { insert(it, counted = false) }
        older[channel] = hasMore && cap < MAX_LOADED_LINES
    }

    internal fun onTyping(channel: ChatChannel, uuid: UUID, name: String): Unit = synchronized(this) {
        if (uuid == selfUuid) return
        typing.getOrPut(channel) { LinkedHashMap() }[uuid] =
            Typist(name, System.currentTimeMillis() + TYPING_TTL_MS)
    }

    internal fun onReady(payload: JsonObject) {
        selfUuid = Uuids.parse(payload.stringOrNull("playerUuid"))
        mute = payload.get("mute")?.takeIf { it.isJsonObject }?.asJsonObject?.let {
            Mute(it.stringOrNull("reason"), expiry(it.stringOrNull("expiresAt")))
        }
    }

    internal fun onSocketLost(): Unit = synchronized(this) {
        typing.clear()
    }

    // Staff lines only reach staff, and the new player might not be staff. Global history is the same for everyone.
    internal fun onAccountSwitched(): Unit = synchronized(this) {
        lines.remove(ChatChannel.STAFF)
        unread.remove(ChatChannel.STAFF)
        backfilled.remove(ChatChannel.STAFF)
        older.remove(ChatChannel.STAFF)
        caps.remove(ChatChannel.STAFF)
        typing.clear()
        refusal = null
    }

    /** Duplicates replace silently, so a reconnect doesn't echo a whole evening again. */
    private fun insert(line: ChatLine, counted: Boolean): Arrival = synchronized(this) {
        val current = lines[line.channel].orEmpty()
        val at = current.binarySearchBy(line.id) { it.id }
        if (at >= 0) {
            val held = current[at]
            lines[line.channel] = current.toMutableList().also { it[at] = line }
            line.pictures.forEach { pictures[it.hash] = it }
            // Discord sends text first and attachments later.
            val known = held.pictures.mapTo(HashSet()) { it.hash }
            return@synchronized when {
                line.pictures.any { it.hash !in known } -> Arrival.PICTURES_ADDED
                line.content != held.content -> Arrival.REVISED
                else -> Arrival.NOTHING
            }
        }

        val next = current.toMutableList().also { it.add(-at - 1, line) }
        val cap = caps[line.channel] ?: MAX_LINES
        lines[line.channel] = if (next.size > cap) ArrayList(next.subList(next.size - cap, next.size)) else next
        line.pictures.forEach { pictures[it.hash] = it }
        line.playerUuid?.let { typing[line.channel]?.remove(it) }
        if (counted && viewing != line.channel) unread.merge(line.channel, 1, Int::plus)
        Arrival.NEW
    }

    private fun expiry(raw: String?): Long? = Timestamps.parse(raw)

    private const val MAX_LINES = 300

    /** How far back paging can go in one session. */
    private const val MAX_LOADED_LINES = 2_000

    private const val MAX_PICTURES = 1024

    /** Longer than the server's 3-second throttle. */
    private const val TYPING_TTL_MS = 5_000L
}
