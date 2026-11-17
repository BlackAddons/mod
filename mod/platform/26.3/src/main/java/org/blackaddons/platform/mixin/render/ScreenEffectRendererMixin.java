package org.blackaddons.platform.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.blackaddons.api.event.EventBus;
import org.blackaddons.api.event.render.FireOverlayRenderEvent;
import org.blackaddons.api.event.render.VisionObstruction;
import org.blackaddons.api.event.render.VisionObstructionEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {

    @Inject(method = "submitFire", at = @At("HEAD"), cancellable = true)
    private static void blackaddons$onSubmitFire(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, TextureAtlasSprite textureAtlasSprite, CallbackInfo ci) {
        FireOverlayRenderEvent event = new FireOverlayRenderEvent(false);
        EventBus.INSTANCE.post(event);
        if (event.getCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "submitBlockSprite", at = @At("HEAD"), cancellable = true)
    private static void blackaddons$onSubmitBlockOverlay(Identifier atlas, float u0, float v0, float u1, float v1, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int color, CallbackInfo ci) {
        if (blackaddons$cleared(VisionObstruction.IN_BLOCK)) ci.cancel();
    }

    @Inject(method = "submitWater", at = @At("HEAD"), cancellable = true)
    private static void blackaddons$onSubmitWater(PlayerRenderState.WaterOverlay waterOverlay, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CallbackInfo ci) {
        if (blackaddons$cleared(VisionObstruction.WATER)) ci.cancel();
    }

    @Unique
    private static boolean blackaddons$cleared(VisionObstruction obstruction) {
        return EventBus.INSTANCE.post(new VisionObstructionEvent(obstruction, false)).getCancelled();
    }
}
