package org.blackaddons.platform.mixin.sanitizer;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.network.chat.Component;
import org.blackaddons.platform.impl.chat.ComponentSanitizer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Blocks mod translation probes via anvil item names. */
@Mixin(AnvilScreen.class)
public abstract class AnvilScreenMixin {

    @WrapOperation(
        method = "slotChanged",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/Component;getString()Ljava/lang/String;")
    )
    private String blackaddons$sanitizeAnvilSlotName(Component instance, Operation<String> original) {
        return ComponentSanitizer.sanitize(instance, "anvil");
    }

    @WrapOperation(
        method = "onNameChanged",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/Component;getString()Ljava/lang/String;")
    )
    private String blackaddons$sanitizeAnvilInputName(Component instance, Operation<String> original) {
        return ComponentSanitizer.sanitize(instance, "anvil");
    }
}
