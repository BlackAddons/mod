package org.blackaddons.core.chat

/**
 * Where a typed line goes. Pure, with exactly three results. There's no "ours but send anyway", because that would leak
 * private lines to the server.
 */
internal sealed interface ChatAddress {

    data object Server : ChatAddress

    /** Doubled prefix: [message] goes to the server with one prefix removed. */
    data class Escaped(val message: String) : ChatAddress

    /** Has to be cancelled even when [text] is empty. */
    data class Ours(val text: String) : ChatAddress
}

internal object ChatPrefix {

    /** `/chat b` and `/chat black`, next to Hypixel's own `/chat` channels. */
    val CHANNEL = setOf("b", "black")

    /** An empty [prefix] disables routing. */
    fun read(message: String, prefix: String): ChatAddress = when {
        prefix.isEmpty() || !message.startsWith(prefix) -> ChatAddress.Server
        message.startsWith(prefix + prefix) -> ChatAddress.Escaped(message.removePrefix(prefix))
        else -> ChatAddress.Ours(message.removePrefix(prefix).trim())
    }
}
