package org.blackaddons.platform.mixin.network;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.blackaddons.api.event.EventBus;
import org.blackaddons.platform.event.impl.PacketEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The innermost public send overload. The others delegate to it, so everything gets caught once. */
@Mixin(Connection.class)
public abstract class ConnectionC2SMixin {

    @Inject(
        method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void blackaddons$c2sPre(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        PacketEvent.C2S.Pre event = new PacketEvent.C2S.Pre(packet, false);
        EventBus.INSTANCE.post(event);
        if (event.getCancelled()) ci.cancel();
    }

    @Inject(
        method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
        at = @At("TAIL")
    )
    private void blackaddons$c2sPost(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        EventBus.INSTANCE.post(new PacketEvent.C2S.Post(packet, false));
    }
}
