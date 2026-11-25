package org.blackaddons.platform.mixin.gui;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.blackaddons.platform.ui.skin.ContainerGlass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** The recipe book panel on container glass; its tabs, slots and toggles are sprites {@code GuiSkin} handles. */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookGlassMixin {
    @ModifyArgs(
        method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit"))
    private void blackaddons$panel(Args args, @Local(argsOnly = true) GuiGraphicsExtractor graphics) {
        ContainerGlass.INSTANCE.recipeBook(graphics, args);
    }
}
