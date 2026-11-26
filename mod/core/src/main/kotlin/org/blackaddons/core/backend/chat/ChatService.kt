package org.blackaddons.core.backend.chat

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.blackaddons.api.Platform
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.chat.IrcMessageEvent
import org.blackaddons.api.event.chat.IrcQuote
import org.blackaddons.api.event.chat.IrcSource
import org.blackaddons.api.util.Uuids
import org.blackaddons.api.util.concurrent.OffThread
import org.blackaddons.api.util.io.parseJsonObject
import org.blackaddons.core.backend.Backend
import org.blackaddons.core.backend.Http
import org.blackaddons.core.backend.gateway.Gateway
import org.blackaddons.core.backend.gateway.Op
import org.blackaddons.core.backend.gateway.stringOrNull
import org.blackaddons.core.chat.ChatMirror
import org.blackaddons.core.emoji.CustomEmojiStore
import org.blackaddons.core.emoji.Emoji
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * BlackAddons chat I/O. Sending is a socket frame, backfill is HTTP. The socket doesn't replay, so recovery is a
 * reconnect and a fetch after the last id. Publishes into [ChatStore].
 */
internal object ChatService {

    private class Pending(
        val channel: ChatChannel,
        val content: String,
        val replyToId: Long?,
        val attachments: List<String>,
        val sentAt: TimeSource.Monotonic.ValueTimeMark,
    )

    /** Unacked lines by nonce. Sending a nonce again returns the original id and doesn't post twice. */
    private val pending = ConcurrentHashMap<String, Pending>()

    private val typingSaidAt = ConcurrentHashMap<ChatChannel, TimeSource.Monotonic.ValueTimeMark>()

    private val olderInFlight = ConcurrentHashMap.newKeySet<ChatChannel>()

    // Paging back is the request a reader can send the fastest. On a 429, the next scroll waits until the limit resets.
    @Volatile
    private var olderBlockedUntil = TimeSource.Monotonic.markNow()

    /** Null until asked. READY has no permissions, so we test access by requesting a page. */
    @Volatile
    var staffAccess: Boolean? = null
        private set

    /**
     * False if not sent. The player hears about it either way. [attachments] are uploaded hashes, and with them the
     * text can be empty.
     */
    fun send(channel: ChatChannel, content: String, replyToId: Long? = null, attachments: List<String> = emptyList()): Boolean {
        val text = Emoji.expandShortcodes(content.trim(), CustomEmojiStore.known)
        if (text.isEmpty() && attachments.isEmpty()) return false
        if (text.length > MAX_CONTENT) {
            refuse(ERROR_INVALID_CONTENT, null)
            return false
        }

        val nonce = UUID.randomUUID().toString()
        val sent = Pending(channel, text, replyToId, attachments, TimeSource.Monotonic.markNow())
        pending[nonce] = sent
        ChatStore.clearRefusal()
        if (say(nonce, sent)) return true

        // Nothing left to retry. If we kept it, it would post later out of context.
        pending.remove(nonce)
        refuse(ERROR_LOST, null)
        return false
    }

    fun typing(channel: ChatChannel) {
        if (typingSaidAt[channel]?.let { it.elapsedNow() < TYPING_INTERVAL } == true) return
        typingSaidAt[channel] = TimeSource.Monotonic.markNow()
        Gateway.say(Op.TYPING_START, JsonObject().apply { addProperty("channel", channel.id) })
    }

    fun backfill(channel: ChatChannel) {
        // Lines that arrived live before the first page are no starting point: `after` would skip the history behind them.
        val after = if (ChatStore.isBackfilled(channel)) ChatStore.lastId(channel) else null
        OffThread.run(
            name = "chat-history",
            work = { Http.get(historyUrl(channel, "after", after), authenticated = true) },
            onFailure = { null },
            onResult = { response ->
                when {
                    response == null -> Unit
                    response.status == 200 -> {
                        val page = parsePage(response.body)
                        ChatStore.onHistory(channel, page.lines, page.hasMore, latest = after == null)
                        if (channel == ChatChannel.STAFF) staffAccess = true
                    }
                    // Only staff reads can be refused, so this tells the tab strip whether to show the staff tab.
                    response.status == 403 && channel == ChatChannel.STAFF -> staffAccess = false
                    else -> Unit
                }
            },
        )
    }

    /**
     * Loads the page before the oldest line we hold, as the reader scrolls up. Only one request per channel runs at a
     * time.
     */
    fun loadOlder(channel: ChatChannel) {
        if (!ChatStore.hasOlder(channel) || !olderBlockedUntil.hasPassedNow()) return
        val before = ChatStore.firstId(channel) ?: return
        if (!olderInFlight.add(channel)) return
        OffThread.run(
            name = "chat-older",
            work = { Http.get(historyUrl(channel, "before", before), authenticated = true) },
            onFailure = { null },
            onResult = { response ->
                when {
                    response?.status == 200 -> parsePage(response.body).let { ChatStore.onOlder(channel, it.lines, it.hasMore) }
                    response?.status == 429 -> olderBlockedUntil = TimeSource.Monotonic.markNow() +
                        (response.retryAfterSeconds ?: OLDER_RETRY_FALLBACK_S).seconds
                    else -> Unit
                }
                olderInFlight.remove(channel)
            },
        )
    }

    fun isLoadingOlder(channel: ChatChannel): Boolean = channel in olderInFlight

    fun probeStaff() {
        if (staffAccess == null) backfill(ChatChannel.STAFF)
    }

    /**
     * Lines from others need moderation permission. "No such line" and "not yours" are both 404, so nobody can probe
     * ids.
     */
    fun delete(id: Long) {
        OffThread.run(
            name = "chat-delete",
            work = { Http.send(Backend.CHAT_URL + "/messages/" + id, method = Http.Method.DELETE, authenticated = true) },
            onFailure = { null },
            onResult = { response ->
                if (response?.status == 204) ChatStore.onDeleted(id) else refuse(ERROR_DELETE_FAILED, null)
            },
        )
    }

    /**
     * MESSAGE and MESSAGE_EDIT (an edit is a whole line). The store decides the echo in game chat, so late Discord
     * pictures and edits update the line instead of printing again.
     */
    fun onMessage(payload: JsonObject) {
        val line = ChatJson.line(payload) ?: return
        CustomEmojiStore.learn(line.content)
        when (ChatStore.onMessage(line)) {
            Arrival.NEW -> {
                ChatMirror.show(line)
                announce(line)
            }
            Arrival.PICTURES_ADDED -> ChatMirror.showLatePictures(line)
            Arrival.REVISED -> ChatMirror.showRevised(line)
            Arrival.NOTHING -> Unit
        }
    }

    // Staff lines stay with the staff: an addon in a staff member's game isn't staff.
    private fun announce(line: ChatLine) {
        if (line.channel != ChatChannel.GLOBAL) return
        val event = IrcMessageEvent(
            id = line.id,
            sender = line.senderName,
            playerUuid = line.playerUuid,
            source = when (line.source) {
                ChatSource.MOD -> IrcSource.GAME
                ChatSource.WEB -> IrcSource.WEBSITE
                ChatSource.DISCORD -> IrcSource.DISCORD
                ChatSource.SYSTEM -> IrcSource.SYSTEM
            },
            text = line.content,
            quote = line.quote?.let { IrcQuote(it.id, it.senderName, it.excerpt) },
            sentAtMs = line.createdAt,
        )
        Platform.screenHost.onClientThread { EventBus.post(event) }
    }

    /** Errors are answers. Closing the socket over a mute would loop reconnects. */
    fun onAck(payload: JsonObject) {
        val nonce = payload.stringOrNull("nonce") ?: return
        val sent = pending.remove(nonce) ?: return
        when (val error = payload.stringOrNull("error")) {
            null -> Unit
            // Sent again before the first one finished. The next socket retries with the same nonce.
            ERROR_IN_PROGRESS -> pending[nonce] = sent
            else -> refuse(error, payload.stringOrNull("detail"))
        }
    }

    /** Comes back to its author too. [ChatStore] filters by uuid. */
    fun onTyping(payload: JsonObject) {
        val channel = ChatChannel.of(payload.stringOrNull("channel")) ?: return
        val uuid = Uuids.parse(payload.stringOrNull("playerUuid")) ?: return
        ChatStore.onTyping(channel, uuid, payload.stringOrNull("name") ?: return)
    }

    /** Sends unacked lines again with the same nonce. Lines older than the server's nonce memory get dropped. */
    // Sending them now would put the previous player's words under the new name. Staff access is per player.
    internal fun onAccountSwitched() {
        if (pending.isNotEmpty()) refuse(ERROR_LOST, null)
        pending.clear()
        staffAccess = null
    }

    fun onReady() {
        pending.entries.removeIf { entry ->
            val expired = entry.value.sentAt.elapsedNow() > NONCE_LIFETIME
            if (expired) refuse(ERROR_LOST, null)
            expired
        }
        pending.forEach { (nonce, sent) -> say(nonce, sent) }
        CustomEmojiStore.refresh()

        if (ChatStore.isBackfilled(ChatChannel.GLOBAL)) backfill(ChatChannel.GLOBAL)
        if (staffAccess == true) backfill(ChatChannel.STAFF)
    }

    /** Recorded for the composer and announced in chat, because the window might be closed. */
    private fun refuse(code: String, detail: String?) {
        ChatStore.onRefused(code, detail)
        ChatMirror.refused(code, detail)
    }

    private fun say(nonce: String, sent: Pending): Boolean = Gateway.say(
        Op.MESSAGE_SEND,
        JsonObject().apply {
            addProperty("nonce", nonce)
            addProperty("channel", sent.channel.id)
            addProperty("content", sent.content)
            sent.replyToId?.let { addProperty("replyToId", it) }
            if (sent.attachments.isNotEmpty()) add("attachments", JsonArray().apply { sent.attachments.forEach(::add) })
        },
    )

    private fun historyUrl(channel: ChatChannel, bound: String, id: Long?): String {
        val base = Backend.CHAT_URL + "/" + channel.id + "/messages?limit=" + PAGE_SIZE
        return if (id == null) base else "$base&$bound=$id"
    }

    private class Page(val lines: List<ChatLine>, val hasMore: Boolean)

    private fun parsePage(body: String): Page {
        val root = parseJsonObject(body) ?: return Page(emptyList(), false)
        val messages = root.get("messages")?.takeIf { it.isJsonArray }?.asJsonArray ?: return Page(emptyList(), false)
        val lines = messages.mapNotNull { element ->
            element?.takeIf { it.isJsonObject }?.asJsonObject?.let(ChatJson::line)
        }
        val hasMore = root.get("hasMore")?.takeIf { it.isJsonPrimitive }?.asBoolean == true
        return Page(lines, hasMore)
    }

    const val MAX_CONTENT = 512
    const val ERROR_LOST = "lost"
    const val ERROR_DELETE_FAILED = "delete_failed"

    private const val ERROR_IN_PROGRESS = "in_progress"

    private const val ERROR_INVALID_CONTENT = "invalid_content"

    private const val PAGE_SIZE = 50

    private const val OLDER_RETRY_FALLBACK_S = 5

    private val TYPING_INTERVAL = 3.seconds

    private val NONCE_LIFETIME = 10.minutes
}
