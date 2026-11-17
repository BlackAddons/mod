package org.blackaddons.platform.mixin.gui;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import org.blackaddons.platform.impl.gui.MenuBackgrounds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/* The 60 is OUT_OF_LEVEL_MENU_LIMIT, the only 60 in getFramerateLimit on every supported version. */
@Mixin(FramerateLimitTracker.class)
public abstract class FramerateLimitTrackerMixin {

    @ModifyConstant(method = "getFramerateLimit", constant = @Constant(intValue = 60))
    private int blackaddons$menuLimit(int vanilla) {
        return MenuBackgrounds.framerateLimit(vanilla);
    }
}
