package org.blackaddons.platform.mixin.render;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.SectionUpdateTracker;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.core.SectionPos;
import org.blackaddons.platform.impl.world.EntityView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/*
 * An entity view must not re-center section tracking, and has to leave chunk loading sets unflipped for the next main
 * frame's occlusion graph.
 */
@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
    @WrapWithCondition(method = "extract", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/SectionUpdateTracker;repositionCamera(Lnet/minecraft/core/SectionPos;)V"))
    private boolean blackaddons$keepSectionTracking(SectionUpdateTracker tracker, SectionPos cameraSectionPos) {
        return !EntityView.getRendering();
    }

    @WrapWithCondition(method = "extract", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientChunkCache;flipUpdateTrackingSets()V"))
    private boolean blackaddons$keepChunkUpdates(ClientChunkCache chunkCache) {
        return !EntityView.getRendering();
    }
}
