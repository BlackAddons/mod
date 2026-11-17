package org.blackaddons.platform.mixin.fme.sodium;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext", remap = false)
public abstract class SodiumBlockRenderContextMixin {
    @Shadow(remap = false) @Final private BlockPos.MutableBlockPos cachedPositionObject;
    @Shadow(remap = false) protected BlockState state;

    @Inject(
        method = "shouldDrawSide",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/block/BlockAndTintGetter;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
            shift = At.Shift.AFTER
        ),
        cancellable = true,
        require = 0,
        remap = false
    )
    private void blackaddons$keepFaceBehindFmeSeeThrough(Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (FmeBlockRemapper.seeThroughFace(this.state, this.cachedPositionObject, direction.getOpposite())) {
            cir.setReturnValue(true);
        }
    }
}
