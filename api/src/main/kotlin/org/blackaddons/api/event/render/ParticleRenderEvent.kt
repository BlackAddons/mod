package org.blackaddons.api.event.render

import org.blackaddons.api.event.Cancellable
import org.blackaddons.api.event.Event

/** Before a single-quad particle renders; cancel to hide it. Coordinates are world space. */
class ParticleRenderEvent(
    val x: Double,
    val y: Double,
    val z: Double,
    override var cancelled: Boolean = false,
) : Event(), Cancellable

/** Before a particle is added to the engine; cancel to suppress. [typeId] e.g. `"minecraft:crit"`. */
class ParticleSpawnEvent(
    val typeId: String,
    val x: Double,
    val y: Double,
    val z: Double,
    override var cancelled: Boolean = false,
) : Event(), Cancellable
