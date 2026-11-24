package org.blackaddons.platform.mixin.gui;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.blackaddons.platform.ui.skin.GuiSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every whole-sprite GUI draw funnels through this overload, so {@link GuiSkin} sees buttons, edit boxes, sliders and
 * container sprites from vanilla and other mods alike. Both versions' descriptors are listed because RenderPipeline
 * changed package in 26.3; only one exists at runtime, and the handler reads only version-neutral arguments.
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiSpriteSkinMixin {

    @Inject(
        method = {
            "blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V",
            "blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V",
        },
        at = @At("HEAD"),
        cancellable = true)
    private void blackaddons$skin(
            CallbackInfo ci,
            @Local(argsOnly = true) Identifier sprite,
            @Local(argsOnly = true, ordinal = 0) int x,
            @Local(argsOnly = true, ordinal = 1) int y,
            @Local(argsOnly = true, ordinal = 2) int width,
            @Local(argsOnly = true, ordinal = 3) int height,
            @Local(argsOnly = true, ordinal = 4) int color
    ) {
        if (GuiSkin.INSTANCE.sprite((GuiGraphicsExtractor) (Object) this, sprite, x, y, width, height, color)) ci.cancel();
    }

    @Inject(
        method = {
            "blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIIIIII)V",
            "blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIIIIII)V",
        },
        at = @At("HEAD"),
        cancellable = true)
    private void blackaddons$skinPart(
            CallbackInfo ci,
            @Local(argsOnly = true) Identifier sprite,
            @Local(argsOnly = true, ordinal = 4) int x,
            @Local(argsOnly = true, ordinal = 5) int y,
            @Local(argsOnly = true, ordinal = 6) int width,
            @Local(argsOnly = true, ordinal = 7) int height,
            @Local(argsOnly = true, ordinal = 8) int color
    ) {
        if (GuiSkin.INSTANCE.partSprite((GuiGraphicsExtractor) (Object) this, sprite, x, y, width, height, color)) ci.cancel();
    }
}
