package org.blackaddons.platform.mixin.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.world.entity.Entity;
import org.blackaddons.api.event.EventBus;
import org.blackaddons.api.event.world.EntityMetadataUpdateEvent;
import org.blackaddons.platform.impl.world.EntitySnapshots;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Emits metadata updates after the packet is applied on the client thread. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerEntityDataMixin {

    @Inject(method = "handleSetEntityData", at = @At("TAIL"))
    private void blackaddons$entityMetadataUpdated(ClientboundSetEntityDataPacket packet, CallbackInfo ci) {
        ClientLevel level = Minecraft.getInstance().level;
        Entity entity = level == null ? null : level.getEntity(packet.id());
        if (entity != null) {
            EventBus.INSTANCE.post(new EntityMetadataUpdateEvent(EntitySnapshots.INSTANCE.of(entity)));
        }
    }
}
