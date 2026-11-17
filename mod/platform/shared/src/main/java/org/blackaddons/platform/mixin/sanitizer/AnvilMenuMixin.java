package org.blackaddons.platform.mixin.sanitizer;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AnvilMenu;
import org.blackaddons.platform.impl.chat.ComponentSanitizer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Blocks mod translation probes via anvil item names. */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {

    @WrapOperation(
        method = "createResult",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/Component;getString()Ljava/lang/String;")
    )
    private String blackaddons$sanitizeAnvilResultName(Component instance, Operation<String> original) {
        return ComponentSanitizer.sanitize(instance, "anvil");
    }
}
