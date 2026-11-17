package org.blackaddons.platform.mixin.fme;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.blackaddons.platform.features.impl.visual.fme.FmeBlockRemapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
    @Inject(method = "submit", at = @At("HEAD"))
    private void blackaddons$beginFmeTint(
        BlockEntityRenderState state,
        PoseStack poseStack,
        SubmitNodeCollector collector,
        CameraRenderState camera,
        CallbackInfo ci
    ) {
        var level = Minecraft.getInstance().level;
        FmeBlockRemapper.setSubmittingBlockEntityTint(
            level == null || state.blockPos == null ? 0xFFFFFF : FmeBlockRemapper.blockEntityTint(level, state.blockPos)
        );
    }

    @Inject(method = "submit", at = @At("RETURN"))
    private void blackaddons$endFmeTint(
        BlockEntityRenderState state,
        PoseStack poseStack,
        SubmitNodeCollector collector,
        CameraRenderState camera,
        CallbackInfo ci
    ) {
        FmeBlockRemapper.setSubmittingBlockEntityTint(0xFFFFFF);
    }
}
