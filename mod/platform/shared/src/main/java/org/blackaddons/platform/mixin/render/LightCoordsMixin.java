package org.blackaddons.platform.mixin.render;

import net.minecraft.util.LightCoordsUtil;
import org.blackaddons.platform.impl.world.ClientLightGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LightCoordsUtil.class)
public abstract class LightCoordsMixin {

    @Inject(
        method = "getLightCoords(Lnet/minecraft/util/LightCoordsUtil$BrightnessGetter;Lnet/minecraft/world/level/BlockAndLightGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void blackaddons$fullBright(CallbackInfoReturnable<Integer> cir) {
        if (ClientLightGate.skips()) cir.setReturnValue(LightCoordsUtil.FULL_BRIGHT);
    }
}
