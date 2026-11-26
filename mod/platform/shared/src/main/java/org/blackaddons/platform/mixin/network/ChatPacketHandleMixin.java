package org.blackaddons.platform.mixin.network;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import org.blackaddons.platform.impl.chat.ChatReceive;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fires {@code ChatReceiveEvent} and {@code ActionBarReceiveEvent} where the connection hands over a chat packet, one
 * step before {@code ClientPacketListener}, so a mod that cancels or rewrites the handler can't starve the listeners.
 * {@code ChatComponentMixin} keeps the cancellable event at the GUI end.
 */
@Mixin({
        ClientboundSystemChatPacket.class,
        ClientboundSetActionBarTextPacket.class,
        ClientboundDisguisedChatPacket.class,
        ClientboundPlayerChatPacket.class,
})
public abstract class ChatPacketHandleMixin {

    @SuppressWarnings("unchecked")
    @Inject(method = "handle(Lnet/minecraft/network/protocol/game/ClientGamePacketListener;)V", at = @At("HEAD"))
    private void blackaddons$received(ClientGamePacketListener listener, CallbackInfo ci) {
        ChatReceive.received((Packet<ClientGamePacketListener>) (Object) this, listener);
    }
}
