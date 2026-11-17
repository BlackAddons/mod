package org.blackaddons.platform.mixin.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.blackaddons.platform.impl.gui.MenuBackgrounds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * extractPanorama, not Panorama.extractRenderState: every screen goes through it, and its signature is the same on
 * every supported version. Its float is the partial tick (vanilla doesn't use it), not an alpha.
 */
@Mixin(Screen.class)
public abstract class ScreenPanoramaMixin {

    @Inject(method = "extractPanorama", at = @At("HEAD"), cancellable = true)
    private void blackaddons$replacePanorama(GuiGraphicsExtractor graphics, float partialTick, CallbackInfo ci) {
        if (MenuBackgrounds.replace(graphics)) ci.cancel();
    }
}
