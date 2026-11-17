package org.blackaddons.platform.mixin.render;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.blackaddons.platform.impl.world.ClientLightGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelLightEngine.class)
public abstract class LevelLightEngineMixin {

    @Inject(method = "getRawBrightness", at = @At("HEAD"), cancellable = true)
    private void blackaddons$fullLight(BlockPos pos, int skyDampen, CallbackInfoReturnable<Integer> cir) {
        if (ClientLightGate.skips()) cir.setReturnValue(15);
    }
}
