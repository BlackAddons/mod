package org.blackaddons.platform.mixin.gui;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CommandSuggestions;
import org.blackaddons.platform.ui.skin.ChatGlass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsGlassMixin {

    @WrapOperation(
        method = "extractUsage",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
    private void blackaddons$glass(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color,
                                   Operation<Void> original) {
        if (!ChatGlass.INSTANCE.usage(graphics, x0, y0, x1, y1)) original.call(graphics, x0, y0, x1, y1, color);
    }
}
