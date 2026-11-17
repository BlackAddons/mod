package org.blackaddons.platform.mixin.world;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.blackaddons.api.event.EventBus;
import org.blackaddons.platform.impl.world.MinecraftBlockChange;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Posts {@link org.blackaddons.api.event.world.BlockChangeEvent}. {@code Level.setBlock} calls this once per real
 * change on the client, from a server update or a prediction alike, so it's the one place that sees them all.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelBlockChangeMixin {

    @Inject(method = "setBlocksDirty", at = @At("HEAD"))
    private void blackaddons$onBlockChanged(BlockPos pos, BlockState before, BlockState after, CallbackInfo ci) {
        if (before == after) return;
        EventBus.INSTANCE.post(new MinecraftBlockChange((ClientLevel) (Object) this, pos.immutable(), before, after));
    }
}
