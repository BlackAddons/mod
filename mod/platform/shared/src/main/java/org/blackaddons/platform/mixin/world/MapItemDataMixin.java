package org.blackaddons.platform.mixin.world;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import org.blackaddons.api.event.EventBus;
import org.blackaddons.platform.event.impl.MapItemDataUpdateEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class MapItemDataMixin {

    @Inject(method = "handleMapItemData", at = @At("TAIL"))
    private void blackaddons$onHandleMapItemData(ClientboundMapItemDataPacket packet, CallbackInfo ci) {
        EventBus.INSTANCE.post(new MapItemDataUpdateEvent(packet));
    }
}
