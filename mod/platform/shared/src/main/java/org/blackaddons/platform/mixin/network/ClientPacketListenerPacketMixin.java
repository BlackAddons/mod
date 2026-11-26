package org.blackaddons.platform.mixin.network;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.*;
import org.blackaddons.platform.event.PacketHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Titles, sounds and container updates as events. The title and sound hooks sit right after the thread guard, so they
 * run once, on the client thread, and can drop the packet. The container hooks run at the end, after the items are in
 * place.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerPacketMixin {

    private static final String GUARD = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread";

    @Inject(method = "setTitleText", at = @At(value = "INVOKE", target = GUARD, shift = At.Shift.AFTER), cancellable = true)
    private void blackaddons$title(ClientboundSetTitleTextPacket packet, CallbackInfo ci) {
        if (PacketHooks.title(packet.text(), false)) ci.cancel();
    }

    @Inject(method = "setSubtitleText", at = @At(value = "INVOKE", target = GUARD, shift = At.Shift.AFTER), cancellable = true)
    private void blackaddons$subtitle(ClientboundSetSubtitleTextPacket packet, CallbackInfo ci) {
        if (PacketHooks.title(packet.text(), true)) ci.cancel();
    }

    @Inject(method = "handleSoundEvent", at = @At(value = "INVOKE", target = GUARD, shift = At.Shift.AFTER), cancellable = true)
    private void blackaddons$sound(ClientboundSoundPacket packet, CallbackInfo ci) {
        if (PacketHooks.sound(packet)) ci.cancel();
    }

    @Inject(method = "handleSoundEntityEvent", at = @At(value = "INVOKE", target = GUARD, shift = At.Shift.AFTER), cancellable = true)
    private void blackaddons$entitySound(ClientboundSoundEntityPacket packet, CallbackInfo ci) {
        if (PacketHooks.entitySound(packet)) ci.cancel();
    }

    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
    private void blackaddons$slot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        PacketHooks.slot(packet);
    }

    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void blackaddons$content(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        PacketHooks.content(packet);
    }
}
