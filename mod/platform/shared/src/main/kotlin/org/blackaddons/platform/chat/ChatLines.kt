package org.blackaddons.platform.chat

import net.minecraft.client.multiplayer.chat.GuiMessage
import net.minecraft.network.chat.Component
import org.blackaddons.api.chat.ChatLine

/**
 * Cursor-to-message lookup, duck-typed onto `ChatComponent` by `ChatComponentMixin`, because it needs private state
 * (wrapped lines, scroll, scale).
 */
interface ChatLines {

    /** The whole message (not the wrapped line) under the GUI-scaled cursor, or null. */
    fun blackaddonsLineAt(mouseX: Double, mouseY: Double): ChatLine?

    /** The message just added. Returned instead of assumed, since our mixin stores a copy with cosmetic names. */
    fun blackaddonsNewestMessage(): GuiMessage?

    /**
     * Replaces [original]'s content, keeping place, age and tag. Returns the new record so later revisions can find it,
     * null once it left history (100 deep).
     */
    fun blackaddonsReplaceMessage(original: GuiMessage, replacement: Component): GuiMessage?

    fun blackaddonsLines(): List<ChatLine>

    fun blackaddonsRemoveMessage(message: GuiMessage)

    fun blackaddonsRefreshView()

    /** Newest first, one JSON line each, for restoring next run. */
    fun blackaddonsSavedMessages(): List<String>
}
