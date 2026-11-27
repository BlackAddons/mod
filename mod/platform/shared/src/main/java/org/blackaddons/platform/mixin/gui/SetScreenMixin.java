package org.blackaddons.platform.mixin.gui;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import org.blackaddons.api.event.EventBus;
import org.blackaddons.platform.event.impl.ScreenEvent;
import org.blackaddons.platform.impl.chat.MinecraftChat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Posts {@code ScreenEvent.Close(old)} before the swap and {@code ScreenEvent.Open(new)} after, no-op on a self-assign.
 * 26.2 moved the screen field and setter onto {@code Gui}.
 */
@Mixin(Gui.class)
public abstract class SetScreenMixin {

    @Shadow private Screen screen;

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void blackaddons$beforeSetScreen(Screen screen, CallbackInfo ci) {
        Screen old = this.screen;
        if (old != screen) {
            EventBus.INSTANCE.post(new ScreenEvent.Close(old));
        }
    }

    @Inject(method = "setScreen", at = @At("RETURN"))
    private void blackaddons$afterSetScreen(Screen screen, CallbackInfo ci) {
        MinecraftChat.INSTANCE.focusChanged(this.screen instanceof ChatScreen);
        EventBus.INSTANCE.post(new ScreenEvent.Open(screen));
    }
}
