package org.blackaddons.platform.mixin.gui;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.blackaddons.platform.ui.skin.ContainerGlass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Screens that draw their own labels instead of the shared ones {@link ContainerGlassMixin} recolors. */
@Mixin({InventoryScreen.class, CreativeModeInventoryScreen.class, MerchantScreen.class})
public abstract class ContainerLabelsGlassMixin {

    @ModifyArg(
        method = "extractLabels",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"),
        index = 4)
    private int blackaddons$labelColor(int color) {
        return ContainerGlass.INSTANCE.labelColor((AbstractContainerScreen<?>) (Object) this, color);
    }
}
