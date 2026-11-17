package org.blackaddons.platform.mixin.sanitizer;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.chat.Component;
import org.blackaddons.platform.impl.chat.ComponentSanitizer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Function;
import java.util.stream.Stream;

/** Blocks mod translation probes via sign lines. */
@Mixin(AbstractSignEditScreen.class)
public abstract class AbstractSignEditScreenMixin {

    @WrapOperation(
        method = "<init>(Lnet/minecraft/world/level/block/entity/SignBlockEntity;ZZLnet/minecraft/network/chat/Component;)V",
        at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;map(Ljava/util/function/Function;)Ljava/util/stream/Stream;")
    )
    private Stream<String> blackaddons$sanitizeSignLines(
        Stream<Component> instance,
        Function<Component, String> function,
        Operation<Stream<String>> original
    ) {
        return original.call(instance, (Function<Component, String>) component -> ComponentSanitizer.sanitize(component, "sign"));
    }
}
