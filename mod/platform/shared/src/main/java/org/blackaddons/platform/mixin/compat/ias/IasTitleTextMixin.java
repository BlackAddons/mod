package org.blackaddons.platform.mixin.compat.ias;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.blackaddons.platform.impl.gui.TitleMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In-Game Account Switcher prints "Current Account" over the middle of the title screen, right through the clean menu.
 * Here the account shows in its icon's tooltip instead. Only applies when IAS is installed.
 */
@Pseudo
@Mixin(targets = "ru.vidtu.ias.IASMinecraft", remap = false)
public abstract class IasTitleTextMixin {

    @Inject(method = "onDraw", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void blackaddons$hideOnCleanMenu(Screen screen, Font font, GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (screen instanceof TitleScreen && TitleMenu.INSTANCE.getEnabled()) ci.cancel();
    }
}
