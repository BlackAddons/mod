package org.blackaddons.core.backend.tickets

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.concurrent.OffThread
import org.blackaddons.api.util.io.parseJsonObject
import org.blackaddons.core.backend.Backend
import org.blackaddons.core.backend.Http
import org.blackaddons.core.backend.chat.ChatStore
import org.blackaddons.core.backend.gateway.objectOrNull
import org.blackaddons.core.backend.gateway.stringOrNull
import org.blackaddons.core.chat.TicketNotice
import org.blackaddons.core.emoji.Emoji

/**
 * Tickets over HTTP plus `TICKET` frames. Staff open them, the player answers. No nonces, because a duplicate reply is
 * the worst case. Missed updates get read again on reconnect.
 */
internal object TicketService {

    private val tr = I18n.scope("blackaddons.tickets")

    @Volatile private var announced = false

    fun refresh(onLoaded: () -> Unit = {}) {
        OffThread.run(
            name = "tickets-list",
            work = { Http.get(Backend.TICKETS_URL, authenticated = true) },
            onFailure = { null },
            onResult = { response ->
                if (response?.status == 200) {
                    val list = parseArray(response.body)?.mapNotNull { element ->
                        element?.takeIf { it.isJsonObject }?.asJsonObject?.let(TicketJson::summary)
                    }
                    if (list != null) TicketStore.onList(list)
                }
                onLoaded()
            },
        )
    }

    fun open(id: Long) {
        OffThread.run(
            name = "tickets-open",
            work = { Http.get(Backend.TICKETS_URL + "/" + id, authenticated = true) },
            onFailure = { null },
            onResult = { response ->
                val ticket = response?.takeIf { it.status == 200 }
                    ?.let { parseJsonObject(it.body) }
                    ?.let(TicketJson::ticket)
                    ?: return@run
                TicketStore.onTicket(ticket)
                markRead(ticket)
            },
        )
    }

    /** [onDone] gets null on success or a localized reason. The composer keeps the text on failure. */
    fun reply(id: Long, text: String, pictures: List<String>, onDone: (String?) -> Unit) {
        val body = JsonObject().apply {
            addProperty("content", Emoji.expandShortcodes(text, emptyList()))
            if (pictures.isNotEmpty()) add("attachments", JsonArray().apply { pictures.forEach(::add) })
        }
        OffThread.run(
            name = "tickets-reply",
            work = {
                Http.send(
                    Backend.TICKETS_URL + "/" + id + "/messages", method = Http.Method.POST,
                    body = body.toString(), contentType = "application/json", authenticated = true,
                )
            },
            onFailure = { null },
            onResult = { response ->
                when {
                    response == null -> onDone(tr("offline", "Could not reach the server"))
                    response.status == 200 -> {
                        parseJsonObject(response.body)
                            ?.let(TicketJson::message)
                            ?.let { message ->
                                // By id, because the player can open another ticket while this request runs.
                                TicketStore.tickets.firstOrNull { it.id == id }?.let { TicketStore.onMessage(it, message, mine = true) }
                            }
                        onDone(null)
                    }
                    else -> onDone(response.error ?: tr("failed", "Could not send"))
                }
            },
        )
    }

    fun onFrame(payload: JsonObject) {
        val summary = payload.objectOrNull("ticket")?.let(TicketJson::summary) ?: return
        when (payload.stringOrNull("kind")) {
            "message" -> {
                val message = payload.objectOrNull("message")?.let(TicketJson::message) ?: return
                val mine = message.authorUuid != null && message.authorUuid == ChatStore.selfUuid
                TicketStore.onMessage(summary, message, mine)
                when {
                    TicketStore.viewing == summary.id -> TicketStore.current?.let(::markRead)
                    !mine && message.author != TicketAuthor.SYSTEM -> TicketNotice.message(summary, message)
                }
            }
            // The server says it. We don't guess from the list.
            "opened" -> {
                TicketStore.onUpdated(summary)
                TicketNotice.opened(summary)
            }
            "updated" -> TicketStore.onUpdated(summary)
            "decided" -> {
                val message = payload.objectOrNull("message")?.let(TicketJson::message)
                if (message != null) TicketStore.onMessage(summary, message, mine = false)
                else TicketStore.onUpdated(summary)
                if (TicketStore.viewing == summary.id) TicketStore.current?.let(::markRead)
                else TicketNotice.decided(summary)
            }
            "removed" -> TicketStore.onRemoved(summary.id)
        }
    }

    fun onAccountSwitched() {
        TicketStore.clear()
        announced = false
    }

    fun onReady() {
        refresh {
            val waiting = TicketStore.unread
            if (waiting > 0 && !announced) {
                announced = true
                TicketNotice.waiting(waiting)
            }
        }
    }

    private fun markRead(ticket: Ticket) {
        val last = ticket.messages.lastOrNull() ?: return
        TicketStore.onRead(ticket.summary.id)
        OffThread.run(
            name = "tickets-read",
            work = {
                Http.send(
                    Backend.TICKETS_URL + "/" + ticket.summary.id + "/read", method = Http.Method.POST,
                    body = JsonObject().apply { addProperty("messageId", last.id) }.toString(),
                    contentType = "application/json", authenticated = true,
                )
            },
            onFailure = { null },
            onResult = {},
        )
    }

    private fun parseArray(body: String): JsonArray? =
        runCatching { JsonParser.parseString(body).asJsonArray }.getOrNull()

    const val MAX_CONTENT = 2000

    const val MAX_PICTURES = 4

    /** Formats the backend checks. Animations are for chat only. */
    val EXTENSIONS = setOf("png", "jpg", "jpeg", "gif")

    const val MAX_PICTURE_BYTES = 8 * 1024 * 1024
}
