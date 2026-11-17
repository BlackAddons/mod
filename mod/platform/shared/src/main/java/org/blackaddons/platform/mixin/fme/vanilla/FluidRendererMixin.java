package org.blackaddons.platform.mixin.fme.vanilla;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.blackaddons.platform.features.impl.visual.fme.FmeBlockRemapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FluidRenderer.class)
public abstract class FluidRendererMixin {
    @Unique private final ThreadLocal<Integer> blackaddons$fmeTint = ThreadLocal.withInitial(() -> 0xFFFFFF);

    @Inject(method = "tesselate", at = @At("HEAD"))
    private void blackaddons$captureFmeTint(
        BlockAndTintGetter level,
        BlockPos pos,
        FluidRenderer.Output output,
        BlockState state,
        FluidState fluidState,
        CallbackInfo ci
    ) {
        this.blackaddons$fmeTint.set(FmeBlockRemapper.tintAt(pos.getX(), pos.getY(), pos.getZ()));
    }

    @ModifyArg(
        method = "vertex",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;addVertex(FFFIFFIIFFF)V"
        ),
        index = 3
    )
    private int blackaddons$applyFmeTint(int color) {
        return FmeBlockRemapper.multiplyTint(color, this.blackaddons$fmeTint.get());
    }
}
