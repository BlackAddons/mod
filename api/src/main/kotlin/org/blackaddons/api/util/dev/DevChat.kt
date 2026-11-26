package org.blackaddons.api.util.dev

import org.blackaddons.api.Platform
import org.blackaddons.api.chat.ChatMessage
import java.awt.Color

/**
 * Debug output to chat, gated here so call sites can't forget. Untranslated and unprefixed on purpose. Gone
 * on restart - use the logger or `Dumpers` for anything persistent.
 */
object DevChat {

    /** Dev environment, or DevMode verbose logging (debugging a real jar). */
    val enabled: Boolean
        get() = DevFlags.isDevelopment || DevFlags.verboseLogging

    /** No-op unless [enabled]. */
    fun log(message: String) {
        if (!enabled) return
        send { append(message, BODY) }
    }

    /** [build] only runs when [enabled]. */
    fun log(build: ChatMessage.Builder.() -> Unit) {
        if (!enabled) return
        send(build)
    }

    private inline fun send(crossinline body: ChatMessage.Builder.() -> Unit) {
        Platform.chat.local(
            ChatMessage.build {
                append(TAG, TAG_COLOR, bold = true)
                body()
            },
        )
    }

    private const val TAG = "[dev] "
    private val TAG_COLOR = Color(0xE8, 0x9B, 0x3C)
    private val BODY = Color(0xAA, 0xAA, 0xAA)
}
