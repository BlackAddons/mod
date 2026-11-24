package org.blackaddons.platform.mixin.gui;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.social.PlayerEntry;
import org.blackaddons.platform.ui.skin.MenuGlass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/* The social interactions screen's player rows; the head shade of a hidden player stays vanilla. */
@Mixin(PlayerEntry.class)
public abstract class PlayerEntryGlassMixin {

    @WrapOperation(
        method = "extractContent",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
    private void blackaddons$row(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color,
                                 Operation<Void> original) {
        if (!MenuGlass.INSTANCE.playerRow(graphics, x0, y0, x1, y1, color)) original.call(graphics, x0, y0, x1, y1, color);
    }
}
