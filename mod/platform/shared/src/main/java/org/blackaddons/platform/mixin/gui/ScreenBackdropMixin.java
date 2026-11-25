package org.blackaddons.platform.mixin.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.blackaddons.platform.ui.skin.SkinGlass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/* The dim behind in-game screens (containers, signs, books): vanilla never blurs these. */
@Mixin(Screen.class)
public abstract class ScreenBackdropMixin {

    @Shadow public int width;
    @Shadow public int height;

    @Inject(method = "extractTransparentBackground", at = @At("HEAD"), cancellable = true)
    private void blackaddons$glass(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (SkinGlass.INSTANCE.backdrop(graphics, width, height)) ci.cancel();
    }
}
