package org.blackaddons.core.backend.tickets

import com.google.gson.JsonObject
import org.blackaddons.api.util.Hashes
import org.blackaddons.api.util.Uuids
import org.blackaddons.core.backend.Timestamps
import org.blackaddons.core.backend.chat.ChatPicture
import org.blackaddons.core.backend.gateway.intOrNull
import org.blackaddons.core.backend.gateway.longOrNull
import org.blackaddons.core.backend.gateway.objectOrNull
import org.blackaddons.core.backend.gateway.stringOrNull
import java.util.UUID

internal enum class TicketAuthor { STAFF, PLAYER, SYSTEM }

internal data class TicketMessage(
    val id: Long,
    val ticketId: Long,
    val author: TicketAuthor,
    val authorName: String,
    val authorUuid: UUID?,
    val content: String,
    val pictures: List<ChatPicture>,
    val createdAt: Long,
)

internal enum class TicketRequestStatus { PENDING, APPROVED, REJECTED }

/** [from]/[to] for name requests. [reason] is the staff's decision note. */
internal data class TicketRequest(
    val kind: String,
    val status: TicketRequestStatus,
    val from: String?,
    val to: String?,
    val reason: String?,
)

internal data class TicketSummary(
    val id: Long,
    val subject: String,
    val open: Boolean,
    val openedBy: String,
    val lastMessageAt: Long,
    val unread: Int,
    val request: TicketRequest? = null,
) {
    val appealable: Boolean get() = !open && request?.status == TicketRequestStatus.REJECTED
}

internal data class Ticket(val summary: TicketSummary, val messages: List<TicketMessage>)

internal object TicketJson {

    fun summary(json: JsonObject): TicketSummary? {
        val id = json.longOrNull("id") ?: return null
        return TicketSummary(
            id = id,
            subject = json.stringOrNull("subject") ?: "",
            open = json.stringOrNull("status") != "Closed",
            openedBy = json.stringOrNull("openedByName") ?: "",
            lastMessageAt = Timestamps.parse(json.stringOrNull("lastMessageAt")) ?: 0L,
            unread = json.intOrNull("unread") ?: 0,
            request = json.objectOrNull("request")?.let(::request),
        )
    }

    private fun request(json: JsonObject): TicketRequest? {
        val payload = json.objectOrNull("payload")
        return TicketRequest(
            kind = json.stringOrNull("kind") ?: return null,
            status = when (json.stringOrNull("status")) {
                "Approved" -> TicketRequestStatus.APPROVED
                "Rejected" -> TicketRequestStatus.REJECTED
                else -> TicketRequestStatus.PENDING
            },
            from = payload?.stringOrNull("from"),
            to = payload?.stringOrNull("to"),
            reason = json.stringOrNull("reason"),
        )
    }

    fun message(json: JsonObject): TicketMessage? {
        val id = json.longOrNull("id") ?: return null
        return TicketMessage(
            id = id,
            ticketId = json.longOrNull("ticketId") ?: return null,
            author = when (json.stringOrNull("kind")) {
                "Staff" -> TicketAuthor.STAFF
                "Player" -> TicketAuthor.PLAYER
                else -> TicketAuthor.SYSTEM
            },
            authorName = json.stringOrNull("authorName") ?: "",
            authorUuid = Uuids.parse(json.stringOrNull("authorPlayerUuid")),
            content = json.stringOrNull("content") ?: "",
            pictures = pictures(json),
            createdAt = Timestamps.parse(json.stringOrNull("createdAt")) ?: System.currentTimeMillis(),
        )
    }

    fun ticket(json: JsonObject): Ticket? {
        val summary = json.objectOrNull("ticket")?.let(::summary) ?: return null
        val messages = json.getAsJsonArray("messages")?.mapNotNull { element ->
            element?.takeIf { it.isJsonObject }?.asJsonObject?.let(::message)
        } ?: emptyList()
        return Ticket(summary, messages)
    }

    private fun pictures(json: JsonObject): List<ChatPicture> =
        json.getAsJsonArray("attachments")?.mapNotNull { element ->
            val entry = element?.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            // Validated, because it goes into a URL.
            val hash = entry.stringOrNull("hash")?.takeIf(Hashes::isSha256Hex) ?: return@mapNotNull null
            ChatPicture(hash, entry.intOrNull("width") ?: 0, entry.intOrNull("height") ?: 0, vector = false)
        } ?: emptyList()
}
