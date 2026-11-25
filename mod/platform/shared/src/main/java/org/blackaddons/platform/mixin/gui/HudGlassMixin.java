package org.blackaddons.platform.mixin.gui;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.blackaddons.platform.ui.skin.HudGlass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/* The scoreboard sidebar's two fills and the action bar's backdrop. */
@Mixin(Hud.class)
public abstract class HudGlassMixin {

    @WrapOperation(
        method = "displayScoreboardSidebar",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 0))
    private void blackaddons$scoreHeader(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color,
                                         Operation<Void> original) {
        if (!HudGlass.INSTANCE.scoreHeader(y0)) original.call(graphics, x0, y0, x1, y1, color);
    }

    @WrapOperation(
        method = "displayScoreboardSidebar",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 1))
    private void blackaddons$scoreBody(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color,
                                       Operation<Void> original) {
        if (!HudGlass.INSTANCE.scoreBody(graphics, x0, x1, y1, color)) original.call(graphics, x0, y0, x1, y1, color);
    }

    @WrapOperation(
        method = "extractOverlayMessage",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;textWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)V"))
    private void blackaddons$actionBar(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int width,
                                       int color, Operation<Void> original) {
        if (!HudGlass.INSTANCE.actionBar(graphics, font, text, x, y, width, color)) {
            original.call(graphics, font, text, x, y, width, color);
        }
    }
}
