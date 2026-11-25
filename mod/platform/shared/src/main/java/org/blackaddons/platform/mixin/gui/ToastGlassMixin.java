package org.blackaddons.platform.mixin.gui;

import net.minecraft.client.gui.components.toasts.RecipeToast;
import net.minecraft.client.gui.components.toasts.TutorialToast;
import org.blackaddons.platform.ui.skin.GuiSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** The toasts that draw dark text, which would vanish on glass. */
@Mixin({RecipeToast.class, TutorialToast.class})
public abstract class ToastGlassMixin {

    @ModifyArg(
        method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text"),
        index = 4)
    private int blackaddons$textColor(int color) {
        return GuiSkin.INSTANCE.toastText(color);
    }
}
