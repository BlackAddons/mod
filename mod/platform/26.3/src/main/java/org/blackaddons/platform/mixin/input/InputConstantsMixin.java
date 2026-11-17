package org.blackaddons.platform.mixin.input;

import com.mojang.blaze3d.platform.InputConstants;
import org.blackaddons.platform.impl.input.SyntheticKeys;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reports {@link SyntheticKeys} as down to anything polling through Minecraft. Raw SDL calls still can't see them. */
@Mixin(InputConstants.class)
public abstract class InputConstantsMixin {

    @Inject(method = "isKeyDown", at = @At("RETURN"), cancellable = true)
    private static void blackaddons$syntheticDown(int key, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && SyntheticKeys.isHeld(key)) cir.setReturnValue(true);
    }
}
