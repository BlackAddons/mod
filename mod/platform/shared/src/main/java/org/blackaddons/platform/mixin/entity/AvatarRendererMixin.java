package org.blackaddons.platform.mixin.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.phys.Vec3;
import org.blackaddons.platform.cosmetics.BlackaddonsRenderState;
import org.blackaddons.platform.cosmetics.PlayerCustomization;
import org.blackaddons.platform.impl.player.MinecraftMovement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Player cosmetic scale and custom cape. {@code AvatarRenderer.scale} doesn't call {@code super}, so the generic {@code
 * LivingEntityRenderer.scale} never sees players. The UUID comes from {@link EntityRendererMixin}.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {

    @Inject(
        method = "scale(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
        at = @At("HEAD")
    )
    private void blackaddons$scale(AvatarRenderState state, PoseStack poseStack, CallbackInfo ci) {
        float[] scale = PlayerCustomization.scaleFor(((BlackaddonsRenderState) state).getBlackaddonsUuid());
        if (scale == null) return;
        if (scale[1] < 0) {
            // Negative Y flips the model: move it back onto the ground and un-invert head pitch.
            poseStack.translate(0.0F, scale[1] * 2.0F, 0.0F);
            state.xRot = -state.xRot;
        }
        poseStack.scale(scale[0], scale[1], scale[2]);
        // Keep the name tag at the scaled model height.
        Vec3 attach = state.nameTagAttachment;
        if (attach != null) {
            state.nameTagAttachment = new Vec3(attach.x, (attach.y + 0.15) * Math.abs(scale[1]), attach.z);
        }
    }

    /** Keeps silent aim visible after vanilla has built the player render state. */
    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
        at = @At("TAIL")
    )
    private void blackaddons$afterExtractRenderState(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
        PlayerCustomization.applyCape(state, entity.getUUID());
        if (entity == Minecraft.getInstance().player) {
            Float silentYaw = MinecraftMovement.INSTANCE.getSilentYaw();
            if (silentYaw != null) {
                state.bodyRot = silentYaw;
                state.yRot = 0.0F;
            }
            Float silentPitch = MinecraftMovement.INSTANCE.getSilentPitch();
            if (silentPitch != null) state.xRot = silentPitch;
        }
    }
}
