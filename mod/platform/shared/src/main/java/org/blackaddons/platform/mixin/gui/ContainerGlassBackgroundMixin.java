package org.blackaddons.platform.mixin.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.AbstractMountInventoryScreen;
import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.client.gui.screens.inventory.CartographyTableScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CrafterScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.DispenserScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.gui.screens.inventory.GrindstoneScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ItemCombinerScreen;
import net.minecraft.client.gui.screens.inventory.LoomScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.gui.screens.inventory.StonecutterScreen;
import org.blackaddons.platform.ui.skin.ContainerGlass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Container glass for every container screen that blits its own background texture: the glass is painted before the
 * first blit, then each blit draws the texture's marks instead of the texture. The blit is matched by name and its
 * arguments read by position, since its RenderPipeline parameter changed package between 26.2 and 26.3.
 */
@Mixin({
    ContainerScreen.class, ShulkerBoxScreen.class, HopperScreen.class, DispenserScreen.class, InventoryScreen.class,
    AbstractFurnaceScreen.class, CraftingScreen.class, CrafterScreen.class, EnchantmentScreen.class,
    BrewingStandScreen.class, ItemCombinerScreen.class, GrindstoneScreen.class, LoomScreen.class,
    StonecutterScreen.class, CartographyTableScreen.class, BeaconScreen.class, MerchantScreen.class,
    AbstractMountInventoryScreen.class, CreativeModeInventoryScreen.class,
})
public abstract class ContainerGlassBackgroundMixin {

    @Inject(
        method = "extractBackground",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit", ordinal = 0))
    private void blackaddons$paintGlass(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        ContainerGlass.INSTANCE.paint((AbstractContainerScreen<?>) (Object) this, graphics, mouseX, mouseY);
    }

    @ModifyArgs(
        method = "extractBackground",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit"))
    private void blackaddons$marksOnly(Args args) {
        ContainerGlass.INSTANCE.background((AbstractContainerScreen<?>) (Object) this, args);
    }
}
