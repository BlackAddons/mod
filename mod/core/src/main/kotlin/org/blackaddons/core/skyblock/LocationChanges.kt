package org.blackaddons.core.skyblock

import org.blackaddons.api.Platform
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.network.ServerLeaveEvent
import org.blackaddons.api.event.skyblock.AreaChangeEvent
import org.blackaddons.api.event.skyblock.ProfileChangeEvent
import org.blackaddons.api.event.subscribe

/**
 * Posts [AreaChangeEvent] and [ProfileChangeEvent]. The sidebar clears both on every server hop, so only a new known
 * value counts as a change.
 */
object LocationChanges {

    private var area: String? = null
    private var profile: String? = null

    fun start() {
        EventBus.subscribe<ClientTickEvent.Post> { check(Platform.skyblock.location, Platform.skyblock.profile) }
        EventBus.subscribe<ServerLeaveEvent> {
            area = null
            profile = null
        }
    }

    internal fun check(nextArea: String?, nextProfile: String?) {
        if (nextArea != null && nextArea != area) {
            val previous = area
            area = nextArea
            EventBus.post(AreaChangeEvent(previous, nextArea))
        }
        if (nextProfile != null && nextProfile != profile) {
            val previous = profile
            profile = nextProfile
            EventBus.post(ProfileChangeEvent(previous, nextProfile))
        }
    }
}
