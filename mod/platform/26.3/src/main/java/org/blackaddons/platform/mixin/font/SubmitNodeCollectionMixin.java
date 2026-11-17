package org.blackaddons.platform.mixin.font;

import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import org.blackaddons.platform.font.MinecraftGlobalFonts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SubmitNodeCollection.class)
public abstract class SubmitNodeCollectionMixin {
    @Inject(method = "canRenderAsSolid", at = @At("HEAD"), cancellable = true)
    private static void blackaddons$keepNativeTextAfterBackgrounds(
        TextFeatureRenderer.Content content, CallbackInfoReturnable<Boolean> cir
    ) {
        if (MinecraftGlobalFonts.isEnabled()) cir.setReturnValue(false);
    }
}
