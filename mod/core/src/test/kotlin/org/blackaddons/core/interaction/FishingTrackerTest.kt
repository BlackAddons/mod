package org.blackaddons.core.interaction

import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.subscribe
import org.blackaddons.api.event.world.FishingRodEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FishingTrackerTest {

    @Test
    fun `posts once per change, not every tick the line stays out`() {
        val seen = mutableListOf<FishingRodEvent>()
        val cast = EventBus.subscribe<FishingRodEvent.Cast> { seen += event }
        val reel = EventBus.subscribe<FishingRodEvent.Reel> { seen += event }
        try {
            listOf(false, true, true, false, false, true).forEach(FishingTracker::update)
            FishingTracker.update(false)
            assertEquals(
                listOf(FishingRodEvent.Cast, FishingRodEvent.Reel, FishingRodEvent.Cast, FishingRodEvent.Reel),
                seen,
            )
        } finally {
            cast.unregister()
            reel.unregister()
        }
    }
}
