package org.blackaddons.platform.mixin.entity;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.blackaddons.api.event.EventBus;
import org.blackaddons.api.event.render.PlayerVisibilityEvent;
import org.blackaddons.core.features.impl.visual.EntityQol;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @Inject(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
        at = @At("HEAD")
    )
    private void blackaddons$hideEntityFire(EntityRenderState state, CameraRenderState camera, double x, double y, double z, PoseStack poseStack, SubmitNodeCollector collector, CallbackInfo ci) {
        if (EntityQol.INSTANCE.enabled && EntityQol.INSTANCE.getHideEntityFire().getValue()) {
            state.displayFireAnimation = false;
        }
    }

    /* Arguments by @Local, because 26.3 added a partial-tick parameter. */
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void blackaddons$playerVisibility(CallbackInfoReturnable<Boolean> cir, @Local(argsOnly = true) Entity entity) {
        if (!(entity instanceof Player player) || entity == Minecraft.getInstance().player) return;
        PlayerVisibilityEvent event = new PlayerVisibilityEvent(
            player.getUUID(), player.getGameProfile().name(), player.getX(), player.getY(), player.getZ(), false
        );
        if (EventBus.INSTANCE.post(event).getCancelled()) cir.setReturnValue(false);
    }
}
