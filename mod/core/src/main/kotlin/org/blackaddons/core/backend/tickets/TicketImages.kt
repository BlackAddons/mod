package org.blackaddons.core.backend.tickets

import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.util.concurrent.LoadOnce
import org.blackaddons.core.backend.Backend
import org.blackaddons.core.backend.Http
import org.blackaddons.core.backend.chat.ChatPicture
import org.blackaddons.core.storage.ContentCache

/**
 * Ticket pictures on disk. They're private to their ticket, so we fetch them with the player's token and then cache
 * them by hash.
 */
internal object TicketImages {

    private val cache = ContentCache(DIR, MAX_BYTES)

    private data class Attachment(val ticketId: Long, val hash: String)

    // A failure is remembered just long enough not to hammer the server.
    private val sources = LoadOnce<Attachment, String>("ticket-image", retryAfterMs = RETRY_AFTER_MS) { (ticketId, hash) ->
        val path = cache.path(hash) ?: Http.bytes(
            Backend.TICKETS_URL + "/" + ticketId + "/attachments/" + hash,
            authenticated = true,
        )?.let { cache.path(cache.put(it)) }
        path?.let { Render.FILE_SCHEME + it.toAbsolutePath() }
    }

    fun draw(ticketId: Long, picture: ChatPicture, x: Number, y: Number, w: Number, h: Number, radius: Number): Boolean {
        val source = sources[Attachment(ticketId, picture.hash)] ?: return false
        Render.image(source, x, y, w, h, radius)
        return true
    }

    private const val DIR = "ticket-images"

    private const val MAX_BYTES = 32L * 1024L * 1024L

    private const val RETRY_AFTER_MS = 30_000L
}
