package org.blackaddons.platform.mixin.command;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import org.blackaddons.platform.impl.command.MinecraftCommands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Re-applies dynamic command aliases after the server replaces the command tree. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerCommandsMixin {

    @Inject(method = "handleCommands", at = @At("TAIL"))
    private void blackaddons$onHandleCommands(ClientboundCommandsPacket packet, CallbackInfo ci) {
        MinecraftCommands.INSTANCE.reapplyDynamicAliases();
    }
}
