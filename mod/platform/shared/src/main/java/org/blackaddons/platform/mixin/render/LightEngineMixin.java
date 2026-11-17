package org.blackaddons.platform.mixin.render;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.lighting.LightEngine;
import org.blackaddons.platform.impl.world.ClientLightGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LightEngine.class)
public abstract class LightEngineMixin {

    @Inject(method = "getLightValue", at = @At("HEAD"), cancellable = true)
    private void blackaddons$fullLight(BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (ClientLightGate.skips()) cir.setReturnValue(15);
    }
}
