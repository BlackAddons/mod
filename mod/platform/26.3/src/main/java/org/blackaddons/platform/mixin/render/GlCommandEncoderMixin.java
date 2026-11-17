package org.blackaddons.platform.mixin.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.mojang.renderpearl.backend.opengl.GlCommandEncoder")
public abstract class GlCommandEncoderMixin {
    /**
     * Keeps the pass's framebuffer bound after a render pass instead of rebinding 0: translucent models are one pass
     * each, so that was two switches per model. Nothing reads the 0, and {@code GlStateManager}'s cache stays true.
     */
    @WrapOperation(
        method = "submitRenderPass",
        at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/backend/opengl/GlStateManager;_glBindFramebuffer(II)V")
    )
    private void blackaddons$keepPassFramebuffer(int target, int framebuffer, Operation<Void> original) {
    }
}
