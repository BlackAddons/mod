package org.blackaddons.platform.mixin.input;

import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(InputConstants.Key.class)
public interface InputConstantsKeyAccessor {
    @Accessor("NAME_MAP")
    static Map<String, InputConstants.Key> blackaddons$nameMap() {
        throw new AssertionError();
    }
}
