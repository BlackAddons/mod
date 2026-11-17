package org.blackaddons.platform.mixin.fme.sodium;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.blackaddons.platform.features.impl.visual.fme.FmeBlockRemapper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.world.LevelSlice", remap = false)
public abstract class SodiumLevelSliceMixin {
    @Shadow(remap = false) @Final private ClientLevel level;

    @Shadow(remap = false) public abstract BlockState getBlockState(int x, int y, int z);

    @Inject(
        method = "getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;",
        at = @At("RETURN"),
        cancellable = true,
        require = 0,
        remap = false
    )
    private void blackaddons$remapFmeState(int x, int y, int z, CallbackInfoReturnable<BlockState> cir) {
        BlockState original = cir.getReturnValue();
        BlockState mapped = FmeBlockRemapper.remap(original, x, y, z);
        if (mapped != original) {
            cir.setReturnValue(mapped);
        }
    }

    @Inject(
        method = "getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
        at = @At("RETURN"),
        cancellable = true,
        require = 0,
        remap = false
    )
    private void blackaddons$remapFmeBlockEntity(BlockPos pos, CallbackInfoReturnable<BlockEntity> cir) {
        BlockEntity original = cir.getReturnValue();
        BlockState state = this.getBlockState(pos.getX(), pos.getY(), pos.getZ());
        BlockEntity mapped = FmeBlockRemapper.remapBlockEntity(original, this.level, pos, state);
        if (mapped != original) {
            cir.setReturnValue(mapped);
        }
    }
}
