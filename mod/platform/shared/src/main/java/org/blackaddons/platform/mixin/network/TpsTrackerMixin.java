package org.blackaddons.platform.mixin.network;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import org.blackaddons.core.tps.TpsTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Feeds {@link TpsTracker} the time-sync packet (every 20 server ticks). Not {@code HEAD}: the netty-thread pass would
 * record every packet twice.
 */
@Mixin(ClientPacketListener.class)
public abstract class TpsTrackerMixin {

    @Inject(
        method = "handleSetTime",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread",
            shift = At.Shift.AFTER
        )
    )
    private void blackaddons$recordTick(ClientboundSetTimePacket packet, CallbackInfo ci) {
        TpsTracker.INSTANCE.recordTick();
    }
}
