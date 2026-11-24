package org.blackaddons.platform.mixin.gui;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.blackaddons.platform.ui.skin.MenuGlass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every textured GUI blit funnels through this overload, so {@link MenuGlass} sees menu backgrounds, lists and
 * separators wherever they are drawn. Both RenderPipeline descriptors are listed, as in {@link GuiSpriteSkinMixin}.
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiTextureSkinMixin {

    @Inject(
        method = {
            "blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V",
            "blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V",
        },
        at = @At("HEAD"),
        cancellable = true)
    private void blackaddons$skin(
            CallbackInfo ci,
            @Local(argsOnly = true) Identifier texture,
            @Local(argsOnly = true, ordinal = 0) int x,
            @Local(argsOnly = true, ordinal = 1) int y,
            @Local(argsOnly = true, ordinal = 0) float u,
            @Local(argsOnly = true, ordinal = 1) float v,
            @Local(argsOnly = true, ordinal = 2) int width,
            @Local(argsOnly = true, ordinal = 3) int height,
            @Local(argsOnly = true, ordinal = 6) int textureWidth,
            @Local(argsOnly = true, ordinal = 7) int textureHeight,
            @Local(argsOnly = true, ordinal = 8) int color
    ) {
        GuiGraphicsExtractor g = (GuiGraphicsExtractor) (Object) this;
        if (MenuGlass.INSTANCE.blit(g, texture, x, y, u, v, width, height, textureWidth, textureHeight, color)) ci.cancel();
    }
}
