package org.blackaddons.api.event.world

import org.blackaddons.api.event.Event
import java.util.UUID

/** Fired from `ClientLevel.addEntity`/`removeEntity`. */
sealed class EntityEvent : Event() {
    class Spawn(val entity: EntitySnapshot) : EntityEvent()
    class Remove(val entity: EntitySnapshot) : EntityEvent()
}

/**
 * Entity state at the moment the event fired; does not update.
 * [skinTextures] is the raw base64 `textures` property - the only stable way to identify an NPC,
 * which respawns with a fresh UUID and name.
 */
data class EntitySnapshot(
    val uuid: UUID,
    val typeId: String,
    val name: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val isPlayer: Boolean,
    val skinTextures: String? = null,
) {
    val isArmorStand: Boolean get() = typeId == "minecraft:armor_stand"
}

/** Tracked data changed. Separate from [EntityEvent.Spawn] because custom names often arrive later. */
class EntityMetadataUpdateEvent(val entity: EntitySnapshot) : Event()
