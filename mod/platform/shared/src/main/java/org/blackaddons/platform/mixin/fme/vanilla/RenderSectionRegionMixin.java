package org.blackaddons.platform.mixin.fme.vanilla;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.blackaddons.platform.features.impl.visual.fme.FmeBlockRemapper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderSectionRegion.class)
public abstract class RenderSectionRegionMixin {
    @Shadow @Final private ClientLevel level;

    @Shadow public abstract BlockState getBlockState(BlockPos pos);

    @Inject(method = "getBlockState", at = @At("RETURN"), cancellable = true)
    private void blackaddons$remapFmeState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        BlockState original = cir.getReturnValue();
        BlockState mapped = FmeBlockRemapper.remap(original, pos.getX(), pos.getY(), pos.getZ(), (BlockGetter) (Object) this, this.level);
        if (mapped != original) {
            cir.setReturnValue(mapped);
        }
    }

    @Inject(method = "getBlockEntity", at = @At("RETURN"), cancellable = true)
    private void blackaddons$remapFmeBlockEntity(BlockPos pos, CallbackInfoReturnable<BlockEntity> cir) {
        BlockEntity original = cir.getReturnValue();
        BlockEntity mapped = FmeBlockRemapper.remapBlockEntity(original, this.level, pos, this.getBlockState(pos));
        if (mapped != original) {
            cir.setReturnValue(mapped);
        }
    }
}
