package org.blackaddons.platform.mixin.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import org.blackaddons.platform.ui.skin.MenuGlass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AdvancementsScreen.class)
public abstract class AdvancementsGlassMixin {

    @Shadow private int leftPos;
    @Shadow private int topPos;

    @Inject(method = "extractInside", at = @At("HEAD"))
    private void blackaddons$card(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        MenuGlass.INSTANCE.advancements(graphics, leftPos, topPos);
    }

    @ModifyArg(
        method = "extractWindow",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text"),
        index = 4)
    private int blackaddons$title(int color) {
        return MenuGlass.INSTANCE.label(color);
    }
}
