package org.blackaddons.platform.mixin.gui;

import net.minecraft.client.Minecraft;
import org.blackaddons.platform.impl.MinecraftDesktop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// createTitle, not updateTitle, because the game runs updateTitle again on every world and screen change.
@Mixin(Minecraft.class)
public abstract class WindowTitleMixin {

    @Inject(method = "createTitle", at = @At("HEAD"), cancellable = true)
    private void blackaddons$overrideTitle(CallbackInfoReturnable<String> cir) {
        String title = MinecraftDesktop.getTitleOverride();
        if (title != null) cir.setReturnValue(title);
    }
}
