package org.blackaddons.platform.mixin.font;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import net.minecraft.util.FormattedCharSequence;
import org.blackaddons.platform.compat.PreparedTextTypes;
import org.blackaddons.platform.font.GlobalFontEffects;
import org.blackaddons.platform.font.GlobalFontRenderContext;
import org.blackaddons.platform.font.MinecraftGlobalFonts;
import org.blackaddons.platform.font.PreparedWorldTextCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(TextFeatureRenderer.class)
public abstract class TextFeatureRendererMixin {
    @WrapMethod(method = "buildGroup")
    private void blackaddons$prepareWorldGlyphs(
        FeatureFrameContext context, List<TextFeatureRenderer.Submit> submits, Operation<Void> original
    ) {
        if (!MinecraftGlobalFonts.isEnabled()) {
            original.call(context, submits);
            return;
        }
        GlobalFontRenderContext.resetWorldGlyphOrder();
        int previous = GlobalFontRenderContext.begin(GlobalFontRenderContext.WORLD);
        float density = GlobalFontRenderContext.getWorldPixelsPerUnit();
        try {
            original.call(context, submits);
        } finally {
            GlobalFontRenderContext.restoreWorldDensity(density);
            GlobalFontEffects.setGlowOutline(0);
            GlobalFontRenderContext.restore(previous);
        }
    }

    @WrapMethod(method = "buildGroup")
    private void blackaddons$reusePreparedText(
        FeatureFrameContext context, List<TextFeatureRenderer.Submit> submits, Operation<Void> original
    ) {
        boolean previous = PreparedTextTypes.getActive();
        PreparedTextTypes.setActive(true);
        try {
            original.call(context, submits);
        } finally {
            PreparedTextTypes.setActive(previous);
        }
    }

    @Inject(
        method = "buildGroup",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/TextFeatureRenderer$Submit;outlineColor()I",
            ordinal = 0
        )
    )
    private void blackaddons$sizeSubmitGlyphs(
        FeatureFrameContext context, List<TextFeatureRenderer.Submit> submits, CallbackInfo ci,
        @Local TextFeatureRenderer.Submit submit
    ) {
        if (!MinecraftGlobalFonts.isEnabled()) return;
        GlobalFontRenderContext.beginWorldDensity(submit.pose());
        GlobalFontEffects.setGlowOutline(submit.outlineColor());
    }

    @WrapOperation(
        method = "buildGroup",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;",
            ordinal = 0
        )
    )
    private Font.PreparedText blackaddons$reuseWorldText(
        Font font, FormattedCharSequence text, float x, float y, int color, boolean shadow, boolean includeEmpty,
        int background, Operation<Font.PreparedText> original
    ) {
        if (includeEmpty) return original.call(font, text, x, y, color, shadow, includeEmpty, background);
        return PreparedWorldTextCache.prepare(
            text, x, y, color, shadow, background,
            () -> original.call(font, text, x, y, color, shadow, includeEmpty, background)
        );
    }
}
