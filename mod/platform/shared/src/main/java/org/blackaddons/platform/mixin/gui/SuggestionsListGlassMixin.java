package org.blackaddons.platform.mixin.gui;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.renderer.Rect2i;
import org.blackaddons.platform.ui.skin.ChatGlass;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/* One popup behind the suggestions in place of vanilla's per-row fills. */
@Mixin(CommandSuggestions.SuggestionsList.class)
public abstract class SuggestionsListGlassMixin {

    @Shadow @Final private Rect2i rect;

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void blackaddons$popup(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        ChatGlass.INSTANCE.suggestions(graphics, rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
    }

    @WrapOperation(
        method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
    private void blackaddons$rows(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color,
                                  Operation<Void> original) {
        if (ChatGlass.INSTANCE.suggestionFill(color)) original.call(graphics, x0, y0, x1, y1, color);
    }

    @ModifyArg(
        method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text"),
        index = 4)
    private int blackaddons$text(int color) {
        return ChatGlass.INSTANCE.suggestionText(color);
    }
}
