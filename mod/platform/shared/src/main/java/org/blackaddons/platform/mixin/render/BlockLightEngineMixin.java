package org.blackaddons.platform.mixin.render;

import net.minecraft.world.level.lighting.BlockLightEngine;
import org.blackaddons.platform.impl.world.ClientLightGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockLightEngine.class)
public abstract class BlockLightEngineMixin {

    @Inject(method = "propagateIncrease", at = @At("HEAD"), cancellable = true)
    private void blackaddons$skipIncrease(long fromNode, long increaseData, int fromLevel, CallbackInfo ci) {
        if (ClientLightGate.skips()) ci.cancel();
    }

    @Inject(method = "propagateDecrease", at = @At("HEAD"), cancellable = true)
    private void blackaddons$skipDecrease(long fromNode, long decreaseData, CallbackInfo ci) {
        if (ClientLightGate.skips()) ci.cancel();
    }
}
