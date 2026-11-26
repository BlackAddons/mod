package org.blackaddons.platform.mixin.render;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.blackaddons.api.event.EventBus;
import org.blackaddons.api.event.render.VisionObstruction;
import org.blackaddons.api.event.render.VisionObstructionEvent;
import org.blackaddons.platform.impl.world.SkyReplacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Both the fog distance and the fog color read this, so swapping the fluid here clears both. */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {

    @Inject(method = "getFogType", at = @At("RETURN"), cancellable = true)
    private void blackaddons$clearFluidFog(Camera camera, CallbackInfoReturnable<FogType> cir) {
        VisionObstruction obstruction = switch (cir.getReturnValue()) {
            case WATER -> VisionObstruction.WATER;
            case LAVA -> VisionObstruction.LAVA;
            default -> null;
        };
        if (obstruction != null && EventBus.INSTANCE.post(new VisionObstructionEvent(obstruction, false)).getCancelled()) {
            cir.setReturnValue(FogType.ATMOSPHERIC);
        }
    }

    /* Near the world's bottom vanilla darkens the fog toward black, which our sky fades into at the horizon. */
    @ModifyExpressionValue(method = "computeFogColor", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(FFF)F"))
    private float blackaddons$noVoidDarknessUnderReplacedSky(float darkness, @Local FogType fogType) {
        return fogType == FogType.ATMOSPHERIC && SkyReplacement.horizon() != null ? 0f : darkness;
    }
}
