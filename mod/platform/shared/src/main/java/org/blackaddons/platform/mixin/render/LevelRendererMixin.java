package org.blackaddons.platform.mixin.render;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ChunkLoadingRenderState;
import net.minecraft.world.phys.Vec3;
import org.blackaddons.platform.impl.world.EntityView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/* Render area, occlusion graph and translucent sort follow the camera. An entity view keeps the player camera's. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @WrapWithCondition(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;repositionCamera(Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"))
    private boolean blackaddons$keepRenderArea(LevelRenderer renderer, CameraRenderState camera) {
        return !EntityView.getRendering();
    }

    @WrapWithCondition(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SectionOcclusionGraph;update(Lnet/minecraft/client/renderer/state/level/CameraRenderState;ILnet/minecraft/client/renderer/state/level/ChunkLoadingRenderState;)V"))
    private boolean blackaddons$keepOcclusionGraph(SectionOcclusionGraph graph, CameraRenderState camera, int fov, ChunkLoadingRenderState chunkLoading) {
        return !EntityView.getRendering();
    }

    @WrapWithCondition(method = "compileSections", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;scheduleTranslucentSectionResort(Lnet/minecraft/world/phys/Vec3;)V"))
    private boolean blackaddons$keepTranslucentSort(LevelRenderer renderer, Vec3 cameraPos) {
        return !EntityView.getRendering();
    }
}
