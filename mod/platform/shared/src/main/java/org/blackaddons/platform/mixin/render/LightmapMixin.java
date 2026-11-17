package org.blackaddons.platform.mixin.render;

import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.blackaddons.api.world.ClientLighting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** With the light engine off every lookup is full light, so the lightmap only needs one update. */
@Mixin(Lightmap.class)
public abstract class LightmapMixin {

    @Unique
    private boolean blackaddons$frozen;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void blackaddons$freeze(LightmapRenderState renderState, CallbackInfo ci) {
        if (!ClientLighting.getDisabled()) {
            blackaddons$frozen = false;
        } else if (blackaddons$frozen) {
            ci.cancel();
        } else if (renderState.needsUpdate) {
            blackaddons$frozen = true;
        }
    }
}
