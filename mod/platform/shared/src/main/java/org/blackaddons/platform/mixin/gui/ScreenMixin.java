package org.blackaddons.platform.mixin.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import org.blackaddons.api.event.EventBus;
import org.blackaddons.api.event.render.CommandConfirmationEvent;
import org.blackaddons.api.event.render.LinkConfirmationEvent;
import org.blackaddons.platform.compat.NativeDesktop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.URI;

@Mixin(Screen.class)
public class ScreenMixin {

    @Inject(method = "clickCommandAction", at = @At("HEAD"), cancellable = true)
    private static void blackaddons$onClickCommandAction(LocalPlayer player, String command, Screen screen, CallbackInfo ci) {
        CommandConfirmationEvent event = new CommandConfirmationEvent(command, false);
        EventBus.INSTANCE.post(event);
        if (event.getCancelled()) {
            if (player != null && player.connection != null) {
                String cmd = command.startsWith("/") ? command.substring(1) : command;
                player.connection.sendCommand(cmd);
                ci.cancel();
            }
        }
    }

    @Inject(method = "clickUrlAction", at = @At("HEAD"), cancellable = true)
    private static void blackaddons$onClickUrlAction(Minecraft mc, Screen screen, URI uri, CallbackInfoReturnable<Boolean> cir) {
        LinkConfirmationEvent event = new LinkConfirmationEvent(uri.toString(), false);
        EventBus.INSTANCE.post(event);
        if (event.getCancelled()) {
            NativeDesktop.INSTANCE.openUri(uri);
            cir.setReturnValue(true);
        }
    }
}
