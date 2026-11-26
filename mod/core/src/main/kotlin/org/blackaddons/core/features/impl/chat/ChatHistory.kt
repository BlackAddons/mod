package org.blackaddons.core.features.impl.chat

import org.blackaddons.api.Platform
import org.blackaddons.api.chat.ChatRetention
import org.blackaddons.api.chat.ChatRetentions
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.util.concurrent.Workers
import org.blackaddons.api.util.io.Disk
import org.blackaddons.core.log.Log

object ChatHistory : Feature(
    description = "Keeps more chat, keeps it when you disconnect or restart, and remembers what you sent across restarts.",
    name = "Chat History",
), ChatRetention {

    private val historyLength by number("History Length", 1000.0, 100.0, 10000.0, 100.0)
        .withDescription("Messages kept in chat, and sent messages kept for the up arrow. Vanilla keeps 100")
    private val keepOnDisconnect by boolean("Keep On Disconnect", true)
        .withDescription("Leaving or switching servers no longer clears the chat")
    private val keepOnRestart by boolean("Keep On Restart", true)
        .withDescription("The chat comes back as it was after restarting the game")
    private val saveSent by boolean("Save Sent Messages", true)
        .withDescription("Messages and commands you sent stay in the up-arrow history after a restart")

    private val writer by lazy { Workers.single("chat-history") }
    private var restored = false
    private var restoredMessages = false

    override fun onEnable() {
        ChatRetentions.register(this)
    }

    override fun onDisable() {
        ChatRetentions.release(this)
        super.onDisable()
    }

    override fun length(vanilla: Int): Int = historyLength.value.toInt()

    override val keepsMessagesOnDisconnect: Boolean get() = keepOnDisconnect.value || keepOnRestart.value

    override val keepsSentOnDisconnect: Boolean get() = keepOnDisconnect.value || saveSent.value

    override val keepsMessagesOnRestart: Boolean get() = keepOnRestart.value

    override fun restoreMessages(): List<String> {
        if (restoredMessages || !keepOnRestart.value) return emptyList()
        restoredMessages = true
        return runCatching { Disk.read(messagesPath()) }
            .onFailure { Log.main.warn("Could not read chat history", it) }
            .getOrNull()?.lines()?.filter { it.isNotBlank() }.orEmpty()
    }

    override fun saveMessages(messages: List<String>) {
        runCatching { Disk.write(messagesPath(), messages.joinToString("\n")) }
            .onFailure { Log.main.warn("Could not save chat history", it) }
    }

    override fun restoreSent(current: List<String>): List<String> {
        if (restored || !saveSent.value) return current
        restored = true
        val saved = runCatching { Disk.read(path()) }
            .onFailure { Log.main.warn("Could not read sent message history", it) }
            .getOrNull()?.lines()?.filter { it.isNotBlank() }.orEmpty()
        return merge(saved, current, historyLength.value.toInt())
    }

    override fun onSent(sent: List<String>) {
        if (!restored || !saveSent.value) return
        val text = sent.takeLast(historyLength.value.toInt()).joinToString("\n")
        writer.execute { Disk.write(path(), text) }
    }

    internal fun merge(saved: List<String>, current: List<String>, limit: Int): List<String> {
        val known = saved.toHashSet()
        return (saved + current.filterNot { it in known }).takeLast(limit)
    }

    private fun path() = Platform.configDir.resolve("chat_history.txt")

    private fun messagesPath() = Platform.configDir.resolve("chat_messages.jsonl")
}
