package org.blackaddons.api.event.world

import org.blackaddons.api.event.Cancellable
import org.blackaddons.api.event.Event

/**
 * The server plays a sound: [id] is its registry id, such as `minecraft:entity.experience_orb.pickup`, at a world
 * position. A sound that follows an entity is reported where that entity is. Cancelling mutes it. Client thread.
 */
class SoundPlayEvent(
    val id: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val volume: Float,
    val pitch: Float,
) : Event(), Cancellable {
    override var cancelled = false
}
