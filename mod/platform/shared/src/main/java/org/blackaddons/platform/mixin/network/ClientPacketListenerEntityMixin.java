package org.blackaddons.platform.mixin.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.blackaddons.api.event.EventBus;
import org.blackaddons.api.event.dungeon.DungeonMimicKilledEvent;
import org.blackaddons.api.skyblock.LocationUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Detects the Mimic's death. After the thread handoff, so it sees direct and bundled packets on the client thread. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerEntityMixin {

    @Inject(
        method = "handleEntityEvent",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread",
            shift = At.Shift.AFTER
        )
    )
    private void blackaddons$entityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        if (packet.getEventId() != 3) return;

        Integer floor = LocationUtils.INSTANCE.getDungeonFloorNumber();
        if (floor == null || floor < 6 || !LocationUtils.INSTANCE.getInDungeon() || LocationUtils.INSTANCE.getInBoss()) {
            return;
        }

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        Entity entity = packet.getEntity(level);
        if (entity instanceof LivingEntity living
            && living.isBaby()
            && "entity.minecraft.zombie".equals(entity.getType().getDescriptionId())) {
            EventBus.INSTANCE.post(DungeonMimicKilledEvent.INSTANCE);
        }
    }
}
