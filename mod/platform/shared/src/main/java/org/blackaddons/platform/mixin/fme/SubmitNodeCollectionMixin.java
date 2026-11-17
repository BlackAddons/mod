package org.blackaddons.platform.mixin.fme;

import net.minecraft.client.renderer.SubmitNodeCollection;
import org.blackaddons.platform.features.impl.visual.fme.FmeBlockRemapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(SubmitNodeCollection.class)
public abstract class SubmitNodeCollectionMixin {
    @ModifyVariable(method = "submitModel", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int blackaddons$tintFmeModel(int tintedColor) {
        return blackaddons$fmeTint(tintedColor);
    }

    @Unique
    private static int blackaddons$fmeTint(int color) {
        int tint = FmeBlockRemapper.getSubmittingBlockEntityTint();
        return tint == 0xFFFFFF ? color : FmeBlockRemapper.multiplyTint(color, tint);
    }
}
