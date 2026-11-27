package org.blackaddons.platform.chat

import net.minecraft.client.multiplayer.chat.GuiMessage
import net.minecraft.network.chat.Component
import org.blackaddons.api.chat.ChatLine
import org.blackaddons.api.ui.layout.Rect

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

    /** Every wrapped row of [message] sits below the open chat's scroll window. */
    fun blackaddonsScrolledPast(message: ChatLine): Boolean

    fun blackaddonsScrollTo(message: ChatLine)

    /** A row of [message] is in the scroll window and the smooth scroll has caught up. */
    fun blackaddonsInView(message: ChatLine): Boolean

    /** The open chat's drawn lines in GUI units, null while it's shut. */
    fun blackaddonsOpenLinesArea(): Rect?

    /** Newest first, one JSON line each, for restoring next run. */
    fun blackaddonsSavedMessages(): List<String>
}
