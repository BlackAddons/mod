package org.blackaddons.platform.mixin.gui;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.reporting.ReportReasonSelectionScreen;
import org.blackaddons.platform.ui.skin.MenuGlass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/* The report reason's description box: a black fill and a white outline. */
@Mixin(ReportReasonSelectionScreen.class)
public abstract class ReportReasonGlassMixin {

    @WrapOperation(
        method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
    private void blackaddons$box(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color,
                                 Operation<Void> original) {
        if (!MenuGlass.INSTANCE.box(graphics, x0, y0, x1, y1)) original.call(graphics, x0, y0, x1, y1, color);
    }

    @WrapOperation(
        method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;outline(IIIII)V"))
    private void blackaddons$outline(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color,
                                     Operation<Void> original) {
        if (!MenuGlass.enabled) original.call(graphics, x, y, w, h, color);
    }
}
