package org.blackaddons.platform.mixin.font;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.network.chat.TextColor;
import org.blackaddons.api.util.color.ColorStop;
import org.blackaddons.api.util.color.GradientColors;
import org.blackaddons.platform.cosmetics.AnimatedTextColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * With {@link AnimatedTextColor} state attached, {@code getValue()} returns the live color for this frame, so stored
 * names (chat lines) keep moving. The math lives in {@link GradientColors} so chat, name tags and tab list all match.
 */
@Mixin(TextColor.class)
public abstract class TextColorMixin implements AnimatedTextColor {

    @Unique private boolean ba$animated;
    @Unique private boolean ba$chroma;
    @Unique private List<ColorStop> ba$stops;
    @Unique private float ba$speed = 1.0f;
    @Unique private float ba$offset;

    @ModifyReturnValue(method = "getValue", at = @At("RETURN"))
    private int blackaddons$animatedValue(int value) {
        if (ba$chroma) {
            return GradientColors.chromaColor(ba$speed, ba$offset, System.currentTimeMillis() / 1000.0);
        }
        if (ba$animated && ba$stops != null && !ba$stops.isEmpty()) {
            return GradientColors.animatedGradientColor(ba$stops, ba$speed, ba$offset, System.currentTimeMillis() / 1000.0);
        }
        return value;
    }

    @Override
    public void baSetAnimated(boolean animated) {
        this.ba$animated = animated;
    }

    @Override
    public void baSetChroma(boolean chroma) {
        this.ba$chroma = chroma;
    }

    @Override
    public void baSetStops(List<ColorStop> stops) {
        this.ba$stops = stops;
    }

    @Override
    public void baSetSpeed(float speed) {
        this.ba$speed = speed;
    }

    @Override
    public void baSetOffset(float offset) {
        this.ba$offset = offset;
    }
}
