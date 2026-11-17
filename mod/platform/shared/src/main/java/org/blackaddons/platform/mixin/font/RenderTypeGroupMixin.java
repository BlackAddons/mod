package org.blackaddons.platform.mixin.font;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.blackaddons.platform.compat.PreparedTextTypes;
import org.blackaddons.platform.font.MinecraftGlobalFonts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer$Group")
public abstract class RenderTypeGroupMixin {
    /*
     * Glyphs span many atlas pages. Merging a page into its first draw puts it before a later text background, which
     * then fails depth under the glyph.
     */
    @ModifyExpressionValue(
        method = "getOrAddDraw",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/renderer/feature/RenderTypeFeatureRenderer$Group;canReorder:Z"
        )
    )
    private boolean blackaddons$keepNativeTextInEmitOrder(boolean canReorder) {
        return canReorder && !(PreparedTextTypes.getActive() && MinecraftGlobalFonts.isEnabled());
    }

    @WrapOperation(
        method = "getOrAddDraw",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/rendertype/RenderType;prepare()Lnet/minecraft/client/renderer/rendertype/PreparedRenderType;"
        )
    )
    private PreparedRenderType blackaddons$reusePreparedText(RenderType renderType, Operation<PreparedRenderType> original) {
        if (!PreparedTextTypes.getActive()) return original.call(renderType);
        PreparedRenderType cached = PreparedTextTypes.cached(renderType);
        if (cached != null) return cached;
        PreparedRenderType prepared = original.call(renderType);
        PreparedTextTypes.store(renderType, prepared);
        return prepared;
    }
}
