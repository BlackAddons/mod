package org.blackaddons.core.emoji

import com.google.gson.JsonParser
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.util.concurrent.LoadOnce
import org.blackaddons.api.util.concurrent.OffThread
import org.blackaddons.core.backend.Backend
import org.blackaddons.core.backend.Http
import org.blackaddons.core.backend.gateway.stringOrNull
import org.blackaddons.core.storage.ContentCache

internal data class CustomEmoji(val id: String, val name: String, val animated: Boolean) {
    val token: String get() = "<${if (animated) "a" else ""}:$name:$id>"
}

/**
 * Custom emoji seen in chat. They show in the picker, expand from `:name:` and draw from backend copies. We never ask
 * Discord.
 */
internal object CustomEmojiStore {

    /** Incremented when a picture is ready, so text gets laid out again. */
    @Volatile var version = 0
        private set

    /** Most recently used first. Emoji seen since join go to the front. */
    @Volatile var known: List<CustomEmoji> = emptyList()
        private set

    private val cache = ContentCache(DIR, MAX_BYTES)
    private val sources = LoadOnce<String, String>("emoji-image", RETRY_AFTER_MS, onLoaded = { _, _ -> version++ }) { id ->
        Http.bytes(Backend.CHAT_URL + "/emoji/" + id, maxBytes = MAX_EMOJI_BYTES)
            ?.let { cache.path(cache.put(it)) }
            ?.let { Render.FILE_SCHEME + it.toAbsolutePath() }
    }

    fun refresh() {
        OffThread.run(
            name = "emoji-list",
            work = { Http.get(Backend.CHAT_URL + "/emoji?limit=" + LIST_LIMIT, authenticated = true) },
            onFailure = { null },
            onResult = { response ->
                if (response?.status != 200) return@run
                val list = runCatching { JsonParser.parseString(response.body).asJsonArray }.getOrNull()
                    ?.mapNotNull { element ->
                        val entry = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                        CustomEmoji(
                            entry.stringOrNull("id") ?: return@mapNotNull null,
                            entry.stringOrNull("name") ?: return@mapNotNull null,
                            entry.get("animated")?.takeIf { it.isJsonPrimitive }?.asBoolean == true,
                        )
                    } ?: return@run
                known = list
            },
        )
    }

    fun learn(text: String) {
        val seen = Emoji.segments(text).filterIsInstance<EmojiSegment.Custom>()
            .map { CustomEmoji(it.id, it.name, it.animated) }
        if (seen.isEmpty()) return
        known = (seen + known).distinctBy { it.id }.take(LIST_LIMIT)
    }

    fun source(id: String): String? = sources[id]

    private const val DIR = "emoji"
    private const val MAX_BYTES = 16L * 1024L * 1024L
    private const val MAX_EMOJI_BYTES = 512 * 1024
    private const val RETRY_AFTER_MS = 30_000L
    private const val LIST_LIMIT = 200
}
