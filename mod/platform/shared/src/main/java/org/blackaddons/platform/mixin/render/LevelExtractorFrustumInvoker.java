package org.blackaddons.platform.mixin.render;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LevelExtractor.class)
public interface LevelExtractorFrustumInvoker {
    @Invoker("applyFrustum")
    void blackaddons$applyFrustum(Frustum frustum);
}
