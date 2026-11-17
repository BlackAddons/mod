package org.blackaddons.platform.mixin.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import org.blackaddons.platform.cosmetics.BlackaddonsRenderState;
import org.blackaddons.platform.cosmetics.Mirroring;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps held items the right way out on an upside-down player (see {@link Mirroring}). The item ends up turned instead
 * of flipped. Mirrored and correctly wound isn't possible with baked culling, and nobody can tell anyway. Targets
 * {@code ItemInHandLayer} since the player subclass doesn't override {@code submit}. Mobs have no scale, so {@code
 * evenOut} returns.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {

    @Inject(
        method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;FF)V",
        at = @At("HEAD")
    )
    private void blackaddons$uprightItem(
            PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords,
            ArmedEntityRenderState state, float yRot, float xRot, CallbackInfo ci
    ) {
        Mirroring.evenOut(poseStack, ((BlackaddonsRenderState) state).getBlackaddonsUuid());
    }

    @Inject(
        method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;FF)V",
        at = @At("RETURN")
    )
    private void blackaddons$restorePose(
            PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords,
            ArmedEntityRenderState state, float yRot, float xRot, CallbackInfo ci
    ) {
        Mirroring.evenOut(poseStack, ((BlackaddonsRenderState) state).getBlackaddonsUuid());
    }
}
