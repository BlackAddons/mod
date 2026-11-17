package org.blackaddons.api.event.world

import org.blackaddons.api.event.Event
import java.util.UUID

/** Server boss bar keyed by [id]. Client thread. */
sealed class BossBarEvent(val id: UUID) : Event() {

    /** Appeared, or [name]/[progress] (0..1) changed. */
    class Update(id: UUID, val name: String, val progress: Float) : BossBarEvent(id)

    class Remove(id: UUID) : BossBarEvent(id)
}
