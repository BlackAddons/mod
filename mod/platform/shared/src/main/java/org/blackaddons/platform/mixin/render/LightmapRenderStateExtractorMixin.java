package org.blackaddons.platform.mixin.render;

import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.blackaddons.api.event.EventBus;
import org.blackaddons.api.event.render.LightmapRenderEvent;
import org.blackaddons.api.event.render.VisionObstruction;
import org.blackaddons.api.event.render.VisionObstructionEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies fullbright and clear-darkness after vanilla prepares the lightmap state. */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapRenderStateExtractorMixin {

    @Inject(method = "extract", at = @At("TAIL"))
    private void blackaddons$adjustLightmap(LightmapRenderState state, float tickDelta, CallbackInfo ci) {
        if (state.darknessEffectScale > 0.0F
            && EventBus.INSTANCE.post(new VisionObstructionEvent(VisionObstruction.DARKNESS, false)).getCancelled()) {
            state.darknessEffectScale = 0.0F;
        }
        if (!EventBus.INSTANCE.post(new LightmapRenderEvent()).getFullbright()) return;

        state.nightVisionEffectIntensity = 1.0F;
        state.nightVisionColor = LightmapRenderStateExtractor.WHITE;
        state.darknessEffectScale = 0.0F;
        state.bossOverlayWorldDarkening = 0.0F;
        state.brightness = 10.0F;
    }
}
