package org.blackaddons.platform.mixin.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import org.blackaddons.platform.impl.gui.MinecraftContainerOverlays;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the chest panel texture under a storage overlay. {@link ContainerScreen#extractBackground} draws it itself,
 * after the {@code super} call that dims the world, so cancel right after that call.
 */
@Mixin(ContainerScreen.class)
public abstract class ContainerScreenRenderMixin {

    @Inject(
        method = "extractBackground",
        at = @At(value = "INVOKE", shift = At.Shift.AFTER,
                 target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"),
        cancellable = true)
    private void blackaddons$hideContainerTexture(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        if (MinecraftContainerOverlays.INSTANCE.suppressesVanilla((AbstractContainerScreen<?>) (Object) this)) ci.cancel();
    }
}
