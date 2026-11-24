package org.blackaddons.platform.mixin.gui;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.DeathScreen;
import org.blackaddons.platform.ui.skin.HudGlass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DeathScreen.class)
public abstract class DeathScreenGlassMixin {

    @WrapOperation(
        method = "extractDeathBackground",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fillGradient(IIIIII)V"))
    private static void blackaddons$glass(
            GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int top, int bottom, Operation<Void> original
    ) {
        if (!HudGlass.INSTANCE.death(graphics, x1 - x0, y1 - y0)) original.call(graphics, x0, y0, x1, y1, top, bottom);
    }
}
