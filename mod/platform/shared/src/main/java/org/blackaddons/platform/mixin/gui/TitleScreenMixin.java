package org.blackaddons.platform.mixin.gui;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.realmsclient.gui.screens.RealmsNotificationsScreen;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.screens.TitleScreen;
import org.blackaddons.platform.impl.gui.TitleMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void blackaddons$arrange(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        TitleMenu.arrange((TitleScreen) (Object) this);
    }

    @WrapWithCondition(
            method = "extractRenderState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/LogoRenderer;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IF)V")
    )
    private boolean blackaddons$logo(LogoRenderer logo, GuiGraphicsExtractor graphics, int width, float alpha) {
        return TitleMenu.showLogo();
    }

    @WrapWithCondition(
            method = "extractRenderState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/SplashRenderer;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;ILnet/minecraft/client/gui/Font;F)V")
    )
    private boolean blackaddons$splash(SplashRenderer splash, GuiGraphicsExtractor graphics, int width, Font font, float alpha) {
        return TitleMenu.showSplash();
    }

    // The version line, the only text TitleScreen draws itself.
    @WrapWithCondition(
            method = "extractRenderState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V")
    )
    private boolean blackaddons$versionLine(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color) {
        return TitleMenu.showVanillaExtras();
    }

    @WrapWithCondition(
            method = "extractRenderState",
            at = @At(value = "INVOKE", target = "Lcom/mojang/realmsclient/gui/screens/RealmsNotificationsScreen;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V")
    )
    private boolean blackaddons$realmsBadges(RealmsNotificationsScreen realms, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        return TitleMenu.showVanillaExtras();
    }
}
