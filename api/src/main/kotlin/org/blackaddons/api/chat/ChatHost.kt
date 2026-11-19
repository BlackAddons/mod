package org.blackaddons.api.chat

import java.awt.Color

/** [local] prints client-side only; [send] and [sendCommand] reach the server and no-op outside a world. */
interface ChatHost {
    fun local(text: String)

    fun local(text: String, color: Color)

    fun local(message: ChatMessage)

    /** Prints [message] and returns a handle to edit it later, or null where the platform can't rewrite drawn lines. */
    fun revisable(message: ChatMessage): RevisableLine?

    /** Sends [text] as chat. No leading `/` - use [sendCommand] for commands. */
    fun send(text: String)

    /**
     * Runs [command] (no leading `/`) as if typed: a client command runs locally, anything else goes to the
     * server. A client command re-sending its own name reaches the server, so aliases can forward.
     */
    fun sendCommand(command: String)

    /** Newest first. On the client thread this is the live list; on any other thread it is a copy. */
    val lines: List<ChatLine> get() = emptyList()

    /** Line under ([x], [y]) in scaled GUI units while the chat is open. */
    fun lineAt(x: Double, y: Double): ChatLine? = null

    fun openInput(text: String) {}

    /** Shows only lines [filter] accepts (null = all). Hidden lines are kept. Client thread only. */
    fun showOnly(filter: ((ChatLine) -> Boolean)?) {}

    /** Removes every chat line. The sent-message history stays. */
    fun clear() {}
}

/** A chat line that can still be edited. Revising is async; a line that left the chat's history can't be changed. */
interface RevisableLine {

    /** No-op once the chat has forgotten the line. */
    fun revise(message: ChatMessage)
}
