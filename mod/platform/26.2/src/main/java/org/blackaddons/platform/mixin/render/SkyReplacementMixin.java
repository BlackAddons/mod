package org.blackaddons.platform.mixin.render;

import net.minecraft.client.renderer.SkyRenderer;
import org.blackaddons.platform.impl.world.CustomSkyRenderer;
import org.blackaddons.platform.impl.world.SkyReplacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/*
 * The overworld sky draws as the disc, sunrise, sun, moon and stars, then the dark disc. A replaced sky draws in place
 * of the disc.
 */
@Mixin(SkyRenderer.class)
public abstract class SkyReplacementMixin {
    @Inject(method = "renderSkyDisc", at = @At("HEAD"), cancellable = true)
    private void blackaddons$drawReplacement(int skyColor, CallbackInfo ci) {
        if (!SkyReplacement.getActive()) return;
        CustomSkyRenderer.draw();
        ci.cancel();
    }

    @Inject(method = {"renderSunriseAndSunset", "renderSunMoonAndStars", "renderDarkDisc"}, at = @At("HEAD"), cancellable = true)
    private void blackaddons$hideVanillaSky(CallbackInfo ci) {
        if (SkyReplacement.getActive()) ci.cancel();
    }
}
