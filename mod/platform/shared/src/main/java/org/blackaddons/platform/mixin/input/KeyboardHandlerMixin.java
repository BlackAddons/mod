package org.blackaddons.platform.mixin.input;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.blackaddons.api.event.EventBus;
import org.blackaddons.api.event.input.KeyInputEvent;
import org.blackaddons.platform.compat.McCommonKt;
import org.blackaddons.platform.impl.gui.MinecraftContainerOverlays;
import org.blackaddons.platform.impl.input.MinecraftKeyNames;
import org.blackaddons.platform.impl.input.SyntheticKeys;
import org.blackaddons.platform.ui.screen.ScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void blackaddons$onKey(long handle, int action, KeyEvent event, CallbackInfo ci) {
        MinecraftKeyNames names = MinecraftKeyNames.INSTANCE;
        EventBus.INSTANCE.post(new KeyInputEvent(
            event.key(), names.action(action), names.modifiers(event.modifiers()), SyntheticKeys.getPressing()));
        if (ScreenOverlay.INSTANCE.key(event.key(), names.action(action), names.modifiers(event.modifiers()))) ci.cancel();
    }

    // fabric-screen-api has no char event, so typed (IME-resolved) characters reach overlays here first.
    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void blackaddons$onCharTyped(long handle, CharacterEvent event, CallbackInfo ci) {
        if (ScreenOverlay.INSTANCE.charTyped(event.codepoint())) {
            ci.cancel();
            return;
        }
        if (McCommonKt.getCurrentScreen(Minecraft.getInstance()) instanceof AbstractContainerScreen<?> screen
                && MinecraftContainerOverlays.INSTANCE.charTyped(screen, event.codepoint())) {
            ci.cancel();
        }
    }
}
