package org.blackaddons.platform.mixin.render;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SkyRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/*
 * The sky renderer keeps its build-time main target, so an entity view would paint sky over the finished frame. Draw
 * into the current target instead.
 */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
    @ModifyExpressionValue(
        method = "render",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/SkyRenderer;renderTarget:Lcom/mojang/blaze3d/pipeline/RenderTarget;", opcode = Opcodes.GETFIELD)
    )
    private RenderTarget blackaddons$currentMainTarget(RenderTarget captured) {
        return Minecraft.getInstance().gameRenderer.mainRenderTarget();
    }
}
