package org.blackaddons.platform.mixin.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.toasts.RecipeToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Vanilla keeps the recipe toast 160 wide, so a wider custom font ran past its edge. */
@Mixin(RecipeToast.class)
public abstract class RecipeToastWidthMixin implements Toast {

    @Shadow @Final private static Component TITLE_TEXT;
    @Shadow @Final private static Component DESCRIPTION_TEXT;

    private static final int VANILLA_WIDTH = 160;
    private static final int TEXT_LEFT = 30;
    private static final int TEXT_RIGHT_PADDING = 6;

    @Override
    public int width() {
        Font font = Minecraft.getInstance().font;
        int text = Math.max(font.width(TITLE_TEXT), font.width(DESCRIPTION_TEXT));
        return Math.max(VANILLA_WIDTH, TEXT_LEFT + text + TEXT_RIGHT_PADDING);
    }
}
