package org.blackaddons.platform.mixin.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.WolfArmorLayer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import org.blackaddons.core.features.impl.visual.EntityQol;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WolfArmorLayer.class)
public abstract class WolfArmorLayerMixin {

    @Inject(
        method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/WolfRenderState;FF)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void blackaddons$hideWolfArmor(PoseStack poseStack, SubmitNodeCollector collector, int light, WolfRenderState state, float yRot, float xRot, CallbackInfo ci) {
        if (EntityQol.INSTANCE.enabled && EntityQol.INSTANCE.getHideArmor().getValue()) {
            ci.cancel();
        }
    }
}
