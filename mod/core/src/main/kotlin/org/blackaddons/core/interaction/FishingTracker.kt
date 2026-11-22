package org.blackaddons.core.interaction

import org.blackaddons.api.Platform
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.subscribe
import org.blackaddons.api.event.world.FishingRodEvent

/** Posts [FishingRodEvent] when the player's line goes out or in. Polled, so a world change reads as a reel. */
object FishingTracker {

    private var fishing = false

    fun start() {
        EventBus.subscribe<ClientTickEvent.Pre> { update(Platform.player.isFishing) }
    }

    internal fun update(nowFishing: Boolean) {
        if (nowFishing == fishing) return
        fishing = nowFishing
        EventBus.post(if (nowFishing) FishingRodEvent.Cast else FishingRodEvent.Reel)
    }
}
