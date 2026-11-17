package org.blackaddons.platform.impl.world
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import org.blackaddons.api.event.world.EntitySnapshot

object EntitySnapshots {
    fun of(entity: Entity): EntitySnapshot {
        val player = entity as? Player
        return EntitySnapshot(
            entity.getUUID(),
            BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString(),
            entity.name.string,
            entity.x, entity.y, entity.z,
            player != null,
            player?.gameProfile?.properties()?.get("textures")?.firstOrNull()?.value(),
        )
    }
}
