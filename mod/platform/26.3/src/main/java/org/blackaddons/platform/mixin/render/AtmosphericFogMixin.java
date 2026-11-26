package org.blackaddons.platform.mixin.render;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import org.blackaddons.platform.impl.world.SkyReplacement;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.awt.*;

/*
 * Far terrain fades into the horizon of a replaced sky instead of vanilla's blue. Effect and boss darkening still
 * apply afterward; FogRendererMixin drops the void darkening.
 */
@Mixin(AtmosphericFogEnvironment.class)
public abstract class AtmosphericFogMixin {
    @Inject(method = "getBaseColor", at = @At("RETURN"), cancellable = true)
    private void blackaddons$replacedSkyHorizon(ClientLevel level, Camera camera, int renderDistance, float partialTicks, CallbackInfoReturnable<Vector3fc> cir) {
        Color horizon = SkyReplacement.horizon();
        if (horizon != null) cir.setReturnValue(new Vector3f(horizon.getRed() / 255f, horizon.getGreen() / 255f, horizon.getBlue() / 255f));
    }
}
