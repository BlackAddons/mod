package org.blackaddons.core.profile.friends

import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.chat.ChatReceiveEvent
import org.blackaddons.api.event.subscribe

/**
 * Learns friends from chat the player already sees (`/f list` pages and friend notices). Never sends commands, because
 * that costs ratelimit and spams chat.
 */
object FriendListWatcher {

    fun start() {
        EventBus.subscribe<ChatReceiveEvent> { onChat(event) }
    }

    private fun onChat(event: ChatReceiveEvent) {
        if (!event.isSystem) return
        val text = event.unformatted

        FriendListParser.nameRemoved(text)?.let {
            Friends.forget(it)
            return
        }
        FriendListParser.nameAdded(text)?.let {
            Friends.remember(listOf(it))
            return
        }
        if (!FriendListParser.looksLikePage(text)) return
        Friends.remember(FriendListParser.namesOnPage(event.message))
    }
}
