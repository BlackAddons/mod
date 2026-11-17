package org.blackaddons.platform.mixin.network;

import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import org.blackaddons.api.event.EventBus;
import org.blackaddons.platform.event.impl.PacketEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class ConnectionS2CMixin {

    @Inject(method = "genericsFtw", at = @At("HEAD"), cancellable = true)
    private static void blackaddons$s2cPre(Packet<?> packet, PacketListener listener, CallbackInfo ci) {
        PacketEvent.S2C.Pre event = new PacketEvent.S2C.Pre(packet, false);
        EventBus.INSTANCE.post(event);
        if (event.getCancelled()) ci.cancel();
    }

    @Inject(method = "genericsFtw", at = @At("TAIL"))
    private static void blackaddons$s2cPost(Packet<?> packet, PacketListener listener, CallbackInfo ci) {
        EventBus.INSTANCE.post(new PacketEvent.S2C.Post(packet, false));
    }
}
