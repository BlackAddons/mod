package org.blackaddons.platform.mixin.font;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.blackaddons.platform.compat.PreparedTextTypes;
import org.blackaddons.platform.font.GlobalFontRenderContext;
import org.blackaddons.platform.font.MinecraftGlobalFonts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/* 26.2 still draws name tags in their own feature renderer, 26.3 sends them through TextFeatureRenderer. */
@Mixin(NameTagFeatureRenderer.class)
public abstract class NameTagFeatureRendererMixin {
    @WrapMethod(method = "buildGroup")
    private void blackaddons$prepareWorldGlyphs(
        FeatureFrameContext context, List<NameTagFeatureRenderer.Submit> submits, Operation<Void> original
    ) {
        if (!MinecraftGlobalFonts.isEnabled()) {
            original.call(context, submits);
            return;
        }
        GlobalFontRenderContext.resetWorldGlyphOrder();
        int previous = GlobalFontRenderContext.begin(GlobalFontRenderContext.WORLD);
        float density = GlobalFontRenderContext.getWorldPixelsPerUnit();
        boolean prepared = PreparedTextTypes.getActive();
        PreparedTextTypes.setActive(true);
        try {
            original.call(context, submits);
        } finally {
            PreparedTextTypes.setActive(prepared);
            GlobalFontRenderContext.restoreWorldDensity(density);
            GlobalFontRenderContext.restore(previous);
        }
    }

    @Inject(
        method = "buildGroup",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/NameTagFeatureRenderer;prepareText(Lnet/minecraft/client/gui/Font;Lnet/minecraft/client/renderer/feature/NameTagFeatureRenderer$Submit;)Lnet/minecraft/client/gui/Font$PreparedText;"
        )
    )
    private void blackaddons$sizeSubmitGlyphs(
        FeatureFrameContext context, List<NameTagFeatureRenderer.Submit> submits, CallbackInfo ci,
        @Local NameTagFeatureRenderer.Submit nameTag
    ) {
        if (MinecraftGlobalFonts.isEnabled()) GlobalFontRenderContext.beginWorldDensity(nameTag.pose());
    }
}
