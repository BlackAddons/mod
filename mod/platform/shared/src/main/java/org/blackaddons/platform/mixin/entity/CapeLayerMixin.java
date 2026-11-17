package org.blackaddons.platform.mixin.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.blackaddons.platform.cosmetics.BlackaddonsRenderState;
import org.blackaddons.platform.cosmetics.Mirroring;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the cape sane on an upside-down player (see {@link Mirroring}). The cape is symmetric, so a half turn lands it
 * where the flip would, and {@code CapeTextures} supplies a pre-rotated texture to fix the art.
 */
@Mixin(CapeLayer.class)
public abstract class CapeLayerMixin {

    @Inject(
        method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
        at = @At("HEAD")
    )
    private void blackaddons$uprightCape(
            PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords,
            AvatarRenderState state, float yRot, float xRot, CallbackInfo ci
    ) {
        Mirroring.evenOut(poseStack, ((BlackaddonsRenderState) state).getBlackaddonsUuid());
    }

    @Inject(
        method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
        at = @At("RETURN")
    )
    private void blackaddons$restorePose(
            PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords,
            AvatarRenderState state, float yRot, float xRot, CallbackInfo ci
    ) {
        Mirroring.evenOut(poseStack, ((BlackaddonsRenderState) state).getBlackaddonsUuid());
    }
}
