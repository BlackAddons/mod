package org.blackaddons.platform.mixin.gui;

import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import org.blackaddons.platform.screenshot.ScreenshotAnnouncer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Consumer;

/**
 * Every written screenshot. Both versions reach this overload via {@code Minecraft}, see {@link ScreenshotAnnouncer}.
 */
@Mixin(Screenshot.class)
public abstract class ScreenshotMixin {

    @ModifyVariable(
        method = "grab(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;ILjava/util/function/Consumer;)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private static Consumer<Component> blackaddons$announce(Consumer<Component> callback) {
        return ScreenshotAnnouncer.wrap(callback);
    }
}
