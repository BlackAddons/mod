package org.blackaddons.api.event.world

import org.blackaddons.api.event.Event

/** The local player's fishing line went out ([Cast]) or came back in ([Reel]), seen at most once per client tick. */
sealed class FishingRodEvent : Event() {
    object Cast : FishingRodEvent()
    object Reel : FishingRodEvent()
}
