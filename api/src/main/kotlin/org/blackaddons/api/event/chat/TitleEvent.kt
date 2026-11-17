package org.blackaddons.api.event.chat

import org.blackaddons.api.event.Cancellable
import org.blackaddons.api.event.Event

/**
 * The server sets the screen title or the line under it. Hypixel sends them apart, so a title and its subtitle are
 * two events. [text] is unformatted and [raw] keeps the `§` codes. Cancelling hides it. Client thread.
 */
sealed class TitleEvent(val text: String, val raw: String) : Event(), Cancellable {
    override var cancelled = false

    class Title(text: String, raw: String) : TitleEvent(text, raw)

    class Subtitle(text: String, raw: String) : TitleEvent(text, raw)
}
