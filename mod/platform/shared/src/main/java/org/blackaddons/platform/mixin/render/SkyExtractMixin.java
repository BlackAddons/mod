package org.blackaddons.platform.mixin.render;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.dimension.DimensionType;
import org.blackaddons.platform.impl.world.SkyReplacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/* Decides once per frame whether the overworld sky gets replaced. The sky draws and the fog read the result. */
@Mixin(SkyRenderer.class)
public abstract class SkyExtractMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void blackaddons$extractReplacement(ClientLevel level, float partialTicks, Camera camera, SkyRenderState state, CallbackInfo ci) {
        SkyReplacement.extract(state.skybox == DimensionType.Skybox.OVERWORLD, state.sunAngle, state.rainBrightness, state.starBrightness);
    }
}
