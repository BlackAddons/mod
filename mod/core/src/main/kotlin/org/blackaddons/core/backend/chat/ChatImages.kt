package org.blackaddons.core.backend.chat

import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.util.concurrent.LoadOnce
import org.blackaddons.core.backend.Backend
import org.blackaddons.core.storage.ContentCache

/**
 * Disk cache for chat pictures. They're immutable by sha256, so we only fetch each one once, unlike the renderer's
 * per-session remote images. [source] is safe while rendering: it reads memory, and the first miss starts one
 * off-thread fetch. Public for the platform's chat preview.
 */
object ChatImages {

    private val cache = ContentCache(DIR, MAX_BYTES)

    private val sources = LoadOnce<String, String>("chat-image") { hash ->
        cache.path(hash, Backend.CHAT_URL + "/attachments/" + hash)?.let { Render.FILE_SCHEME + it.toAbsolutePath() }
    }

    /** Null until loaded. Safe while rendering. Max one fetch per hash per game. */
    fun source(hash: String): String? = sources[hash]

    fun draw(picture: ChatPicture, x: Number, y: Number, w: Number, h: Number, radius: Number): Boolean {
        val source = source(picture.hash) ?: return false
        // Lottie paints itself. Clipping would cost a layer per frame.
        if (picture.vector) Render.lottie(source, x, y, w, h) else Render.image(source, x, y, w, h, radius)
        return true
    }

    private const val DIR = "chat-images"
    private const val MAX_BYTES = 64L * 1024L * 1024L
}
