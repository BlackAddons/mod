package org.blackaddons.platform.mixin.sanitizer;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.chat.Component;
import org.blackaddons.platform.impl.chat.ComponentSanitizer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Blocks mod translation probes via sign lines. */
@Mixin(AbstractSignEditScreen.class)
public abstract class AbstractSignEditScreenMixin {

    @WrapOperation(
        method = "<init>(Lnet/minecraft/world/level/block/entity/SignBlockEntity;Lnet/minecraft/world/level/block/entity/SignTextSlot;ZLnet/minecraft/network/chat/Component;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/Component;getString()Ljava/lang/String;")
    )
    private String blackaddons$sanitizeSignLine(Component component, Operation<String> original) {
        return ComponentSanitizer.sanitize(component, "sign");
    }
}
