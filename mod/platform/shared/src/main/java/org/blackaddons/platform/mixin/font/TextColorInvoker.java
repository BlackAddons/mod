package org.blackaddons.platform.mixin.font;

import net.minecraft.network.chat.TextColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes the private {@code (int)} constructor to mint non-interned instances, one per animated character. */
@Mixin(TextColor.class)
public interface TextColorInvoker {
    @Invoker("<init>")
    static TextColor baCreate(int value) {
        throw new UnsupportedOperationException();
    }
}
