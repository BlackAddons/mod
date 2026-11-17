package org.blackaddons.platform.mixin.network;

import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerboundUseItemPacket.class)
public interface ServerboundUseItemPacketAccessor {

    @Mutable
    @Accessor("yRot")
    void blackaddons$setYaw(float yaw);

    @Mutable
    @Accessor("xRot")
    void blackaddons$setPitch(float pitch);
}
