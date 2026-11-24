package org.blackaddons.platform.mixin.gui;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import org.blackaddons.platform.ui.skin.HudGlass;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/* The tab list's header, body and footer boxes (fills 0, 1 and 3); the per-player cells (fill 2) stay vanilla. */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayGlassMixin {

    @Shadow private @Nullable Component header;
    @Shadow private @Nullable Component footer;

    @WrapOperation(
        method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 0))
    private void blackaddons$header(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color,
                                    Operation<Void> original) {
        if (!HudGlass.INSTANCE.tabHeader(graphics, x0, y0, x1, y1)) original.call(graphics, x0, y0, x1, y1, color);
    }

    @WrapOperation(
        method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 1))
    private void blackaddons$body(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color,
                                  Operation<Void> original) {
        if (!HudGlass.INSTANCE.tabBody(graphics, x0, y0, x1, y1, header != null, footer != null)) {
            original.call(graphics, x0, y0, x1, y1, color);
        }
    }

    @WrapOperation(
        method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 3))
    private void blackaddons$footer(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color,
                                    Operation<Void> original) {
        if (!HudGlass.INSTANCE.tabFooter(graphics, x0, y0, x1, y1)) original.call(graphics, x0, y0, x1, y1, color);
    }
}
