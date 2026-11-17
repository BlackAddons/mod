package org.blackaddons.platform.mixin.world;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.blackaddons.api.event.EventBus;
import org.blackaddons.api.event.world.EntityEvent;
import org.blackaddons.platform.impl.world.EntitySnapshots;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Posts {@link EntityEvent.Spawn} and {@link EntityEvent.Remove}. Snapshots are taken at event time. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelEntityMixin {

    @Inject(method = "addEntity", at = @At("TAIL"))
    private void blackaddons$onAddEntity(Entity entity, CallbackInfo ci) {
        EventBus.INSTANCE.post(new EntityEvent.Spawn(EntitySnapshots.INSTANCE.of(entity)));
    }

    @Inject(method = "removeEntity", at = @At("HEAD"))
    private void blackaddons$onRemoveEntity(int id, Entity.RemovalReason reason, CallbackInfo ci) {
        ClientLevel self = (ClientLevel) (Object) this;
        Entity entity = self.getEntity(id);
        if (entity != null) {
            EventBus.INSTANCE.post(new EntityEvent.Remove(EntitySnapshots.INSTANCE.of(entity)));
        }
    }
}
