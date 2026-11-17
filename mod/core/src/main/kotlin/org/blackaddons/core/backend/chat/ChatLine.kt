package org.blackaddons.core.backend.chat

import com.google.gson.JsonObject
import org.blackaddons.api.util.Hashes
import org.blackaddons.api.util.Uuids
import org.blackaddons.core.backend.Timestamps
import org.blackaddons.core.backend.gateway.intOrNull
import org.blackaddons.core.backend.gateway.longOrNull
import org.blackaddons.core.backend.gateway.stringOrNull
import java.net.URI
import java.util.UUID

/** Closed list. Who the player is decides which channels they're in. */
enum class ChatChannel(val id: String) {
    GLOBAL("global"),
    STAFF("staff"),
    ;

    companion object {
        fun of(id: String?): ChatChannel? = entries.firstOrNull { it.id == id }
    }
}

/** Where a line came from (game, panel, Discord), not who said it. */
enum class ChatSource {
    MOD,
    WEB,
    DISCORD,
    SYSTEM,
    ;

    companion object {
        fun of(raw: String?): ChatSource = entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: MOD
    }
}

/**
 * The server freezes it, so edits and deletions can't change what a reply answered. [playerUuid] lets the quoted name
 * show its cosmetic. Quotes frozen before that field existed don't have it.
 */
data class ChatQuote(val id: Long, val senderName: String, val excerpt: String, val playerUuid: UUID? = null)

/** [hash] is the full address under [org.blackaddons.core.backend.Backend.CHAT_URL]. */
data class ChatPicture(
    val hash: String,
    val width: Int,
    val height: Int,
    /** Lottie (usually a Discord sticker). Only changes the renderer, fetch and layout stay the same. */
    val vector: Boolean = false,
)

data class ChatLine(
    val id: Long,
    val channel: ChatChannel,
    val playerUuid: UUID?,
    val senderName: String,
    val source: ChatSource,
    val content: String,
    val createdAt: Long,
    /** Time of the last text edit. Late Discord pictures arrive as edits but don't set it, so "(edited)" uses this. */
    val editedAt: Long?,
    val quote: ChatQuote?,
    val pictures: List<ChatPicture>,
    /**
     * The Discord avatar of a relayed line. Only set for authors without [playerUuid], the others show their Minecraft
     * face.
     */
    val discordAvatar: String? = null,
) {
    val edited: Boolean get() = editedAt != null

    /**
     * [content] with the links removed when the line has a picture (the bridge already turned the link into the
     * picture). [content] stays as written, and copy uses it.
     */
    val shown: String by lazy(LazyThreadSafetyMode.NONE) {
        if (links.isEmpty()) content else content.replace(URL, " ").replace(GAPS, " ").trim()
    }

    /** Links removed from [shown]. All or none, because we don't know which link made which picture. */
    val links: List<String> by lazy(LazyThreadSafetyMode.NONE) {
        if (pictures.isEmpty()) emptyList() else URL.findAll(content).map { it.value }.toList()
    }

    /** Only links: the bridge attaches the picture soon. The web calls it `isOnlyLinks`. */
    val onlyLinks: Boolean by lazy(LazyThreadSafetyMode.NONE) {
        content.isNotBlank() && content.replace(URL, " ").isBlank()
    }
}

private const val LOTTIE_CONTENT_TYPE = "application/lottie+json"

/** Bare http(s) links. Discord only turns this form into a picture. */
private val URL = Regex("""https?://\S+""")

private val GAPS = Regex("""\s{2,}""")

/** Every field is optional. A half-formed line gets dropped. */
internal object ChatJson {

    fun line(payload: JsonObject): ChatLine? {
        val id = payload.longOrNull("id") ?: return null
        val channel = ChatChannel.of(payload.stringOrNull("channel")) ?: return null
        val content = payload.get("content")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
        val pictures = pictures(payload)
        if (content.isBlank() && pictures.isEmpty()) return null

        return ChatLine(
            id = id,
            channel = channel,
            playerUuid = Uuids.parse(payload.stringOrNull("playerUuid")),
            senderName = payload.stringOrNull("senderName") ?: UNKNOWN_SENDER,
            source = ChatSource.of(payload.stringOrNull("source")),
            content = content,
            createdAt = millis(payload.stringOrNull("createdAt")),
            editedAt = payload.stringOrNull("editedAt")?.let { millis(it) },
            quote = quote(payload),
            pictures = pictures,
            discordAvatar = payload.stringOrNull("avatarUrl")?.takeIf { isDiscordCdn(it) },
        )
    }

    /** The backend only lets these through. Checked again here because the address gets fetched as it is. */
    private fun isDiscordCdn(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.scheme == "https" && uri.port == -1 && uri.rawUserInfo == null &&
            uri.host.equals(DISCORD_CDN, ignoreCase = true)
    }

    private fun quote(payload: JsonObject): ChatQuote? {
        val reply = payload.get("replyTo")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val id = reply.longOrNull("id") ?: return null
        return ChatQuote(
            id = id,
            senderName = reply.stringOrNull("senderName") ?: UNKNOWN_SENDER,
            excerpt = reply.stringOrNull("excerpt") ?: "",
            playerUuid = Uuids.parse(reply.stringOrNull("playerUuid")),
        )
    }

    private fun pictures(payload: JsonObject): List<ChatPicture> {
        val array = payload.get("attachments")?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            val entry = element?.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            // Validated, because it goes into a URL.
            val hash = entry.stringOrNull("hash")?.takeIf(Hashes::isSha256Hex) ?: return@mapNotNull null
            ChatPicture(
                hash,
                entry.intOrNull("width") ?: 0,
                entry.intOrNull("height") ?: 0,
                vector = entry.stringOrNull("contentType") == LOTTIE_CONTENT_TYPE,
            )
        }
    }


    /** An unreadable time reads as now, so the line can still be sorted. */
    private fun millis(raw: String?): Long = Timestamps.parse(raw) ?: System.currentTimeMillis()

    private const val UNKNOWN_SENDER = "unknown"

    private const val DISCORD_CDN = "cdn.discordapp.com"
}
