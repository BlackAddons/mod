package org.blackaddons.platform.mixin.fme;

import net.minecraft.client.renderer.texture.atlas.SpriteSourceList;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.blackaddons.platform.features.impl.visual.fme.FmeEntitySpriteSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpriteSourceList.class)
public abstract class SpriteSourceListMixin {
    @Inject(method = "load", at = @At("RETURN"))
    private static void blackaddons$addFmeEntityFaces(ResourceManager resources, Identifier atlasId, CallbackInfoReturnable<SpriteSourceList> cir) {
        if (Identifier.withDefaultNamespace("blocks").equals(atlasId)) {
            ((SpriteSourceListAccessor) cir.getReturnValue()).blackaddons$getSources().add(FmeEntitySpriteSource.INSTANCE);
        }
    }
}
