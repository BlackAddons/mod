package org.blackaddons.platform.mixin.render;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import org.blackaddons.platform.impl.world.SkyReplacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.awt.*;

/*
 * Far terrain fades into the horizon of a replaced sky instead of vanilla's blue. Vanilla's darkening still applies
 * afterward.
 */
@Mixin(AtmosphericFogEnvironment.class)
public abstract class AtmosphericFogMixin {
    @Inject(method = "getBaseColor", at = @At("RETURN"), cancellable = true)
    private void blackaddons$replacedSkyHorizon(ClientLevel level, Camera camera, int renderDistance, float partialTicks, CallbackInfoReturnable<Integer> cir) {
        Color horizon = SkyReplacement.horizon();
        if (horizon != null) cir.setReturnValue(horizon.getRGB());
    }
}
