package org.blackaddons.platform.mixin.compat.xeno;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.blackaddons.platform.features.impl.visual.fme.FmeBlockRemapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.xeno.client.common.render.block.XenoFluidRenderer", remap = false)
public abstract class XenoFluidRendererMixin {
    @Unique private final ThreadLocal<Integer> blackaddons$fmeTint = ThreadLocal.withInitial(() -> 0xFFFFFF);

    @Inject(method = "tesselate", at = @At("HEAD"), remap = false)
    private void blackaddons$captureFmeTint(
        BlockAndTintGetter level,
        BlockPos pos,
        @Coerce Object output,
        BlockState blockState,
        FluidState fluidState,
        CallbackInfo ci
    ) {
        this.blackaddons$fmeTint.set(FmeBlockRemapper.tintAt(pos.getX(), pos.getY(), pos.getZ()));
    }

    @ModifyArg(
        method = "tesselate",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/ARGB;scaleRGB(IF)I"
        ),
        index = 0,
        remap = false
    )
    private int blackaddons$applyFmeTint(int color) {
        return FmeBlockRemapper.multiplyTint(color, this.blackaddons$fmeTint.get());
    }
}
