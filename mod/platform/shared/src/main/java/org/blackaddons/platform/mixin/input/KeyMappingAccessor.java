package org.blackaddons.platform.mixin.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Private press state, so {@code MinecraftInteraction} can fire synthetic clicks the way a real tap does. */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
    @Accessor("key")
    InputConstants.Key blackaddons$getBoundKey();

    @Accessor("clickCount")
    int blackaddons$getClickCount();

    @Accessor("clickCount")
    void blackaddons$setClickCount(int clickCount);

    @Accessor("isDown")
    void blackaddons$setDown(boolean down);
}
