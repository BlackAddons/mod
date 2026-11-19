package org.blackaddons.platform.mixin.control;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.blackaddons.api.camera.CameraController;
import org.blackaddons.api.camera.CameraControllers;
import org.blackaddons.api.event.EventBus;
import org.blackaddons.api.event.render.NightVisionRenderEvent;
import org.blackaddons.platform.impl.player.rotation.MinecraftRotation;
import org.blackaddons.platform.impl.world.EntityView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "extract", at = @At("HEAD"))
    private void blackaddons$beforeExtract(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        MinecraftRotation.INSTANCE.onFrame(deltaTracker.getGameTimeDeltaTicks());
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void blackaddons$onRenderItemInHand(CallbackInfo ci) {
        CameraController controller = CameraControllers.active();
        if (EntityView.getRendering() || controller != null && controller.getHideHands()) {
            ci.cancel();
        }
    }

    /* Fog is mapped memory written in place: an entity view's write would land under the main frame's queued draws. */
    @WrapWithCondition(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;updateBuffer(Lnet/minecraft/client/renderer/fog/FogData;)V"))
    private boolean blackaddons$keepMainFog(FogRenderer fogRenderer, FogData fog) {
        return !EntityView.getRendering();
    }

    @Inject(method = "nightVisionScale", at = @At("HEAD"), cancellable = true)
    private static void blackaddons$disableNightVision(LivingEntity camera, float a, CallbackInfoReturnable<Float> cir) {
        if (EventBus.INSTANCE.post(new NightVisionRenderEvent(false)).getCancelled()) cir.setReturnValue(0.0F);
    }
}
