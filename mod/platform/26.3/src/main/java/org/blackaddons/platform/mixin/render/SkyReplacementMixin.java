package org.blackaddons.platform.mixin.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.dimension.DimensionType;
import org.blackaddons.platform.impl.world.CustomSkyRenderer;
import org.blackaddons.platform.impl.world.SkyReplacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/* A replaced overworld sky draws instead of vanilla's whole sky pass. The End sky stays vanilla. */
@Mixin(SkyRenderer.class)
public abstract class SkyReplacementMixin {
    @Inject(
        method = "render(Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lnet/minecraft/client/renderer/state/level/SkyRenderState;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void blackaddons$drawReplacement(GpuBufferSlice skyFog, SkyRenderState state, CallbackInfo ci) {
        if (state.skybox != DimensionType.Skybox.OVERWORLD || !SkyReplacement.getActive()) return;
        RenderSystem.setShaderFog(skyFog);
        CustomSkyRenderer.draw();
        ci.cancel();
    }
}
