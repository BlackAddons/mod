package org.blackaddons.platform.mixin.gui;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import org.blackaddons.platform.ui.skin.MenuGlass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/* Every list's selected row (worlds, servers, packs, languages, chat report): an outline fill, then black inside. */
@Mixin(AbstractSelectionList.class)
public abstract class SelectionListGlassMixin {

    @WrapOperation(
        method = "extractSelection",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 0))
    private void blackaddons$outline(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color,
                                     Operation<Void> original) {
        if (!MenuGlass.INSTANCE.selection(graphics, x0, y0, x1, y1, color)) original.call(graphics, x0, y0, x1, y1, color);
    }

    @WrapOperation(
        method = "extractSelection",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 1))
    private void blackaddons$inside(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color,
                                    Operation<Void> original) {
        if (!MenuGlass.enabled) original.call(graphics, x0, y0, x1, y1, color);
    }
}
