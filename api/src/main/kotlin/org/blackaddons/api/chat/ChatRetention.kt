package org.blackaddons.api.chat

/** What the chat keeps: length, persistence across disconnects and sent history. Defaults are vanilla. */
interface ChatRetention {

    /** Stored messages and sent history size, where vanilla keeps [vanilla]. */
    fun length(vanilla: Int): Int = vanilla

    val keepsMessagesOnDisconnect: Boolean get() = false

    val keepsSentOnDisconnect: Boolean get() = false

    val keepsMessagesOnRestart: Boolean get() = false

    /** Messages saved by the last run, newest first. Asked before every new message. */
    fun restoreMessages(): List<String> = emptyList()

    /** The whole chat at shutdown, newest first, for the next run. */
    fun saveMessages(messages: List<String>) {}

    /** Sent history to use given [current]; asked each time the chat opens. */
    fun restoreSent(current: List<String>): List<String> = current

    /** The whole sent history, right after the player sent a line. */
    fun onSent(sent: List<String>) {}
}

/** The registered [ChatRetention]; last registration wins. */
object ChatRetentions {

    @Volatile
    private var current: ChatRetention? = null

    fun register(retention: ChatRetention) {
        current = retention
    }

    fun release(retention: ChatRetention) {
        if (current === retention) current = null
    }

    fun length(vanilla: Int): Int = current?.length(vanilla) ?: vanilla

    val keepsMessagesOnDisconnect: Boolean get() = current?.keepsMessagesOnDisconnect == true

    val keepsSentOnDisconnect: Boolean get() = current?.keepsSentOnDisconnect == true

    val keepsMessagesOnRestart: Boolean get() = current?.keepsMessagesOnRestart == true

    fun restoreMessages(): List<String> = current?.restoreMessages().orEmpty()

    fun saveMessages(messages: List<String>) {
        current?.saveMessages(messages)
    }

    fun restoreSent(current: List<String>): List<String> = this.current?.restoreSent(current) ?: current

    fun onSent(sent: List<String>) {
        current?.onSent(sent)
    }
}
