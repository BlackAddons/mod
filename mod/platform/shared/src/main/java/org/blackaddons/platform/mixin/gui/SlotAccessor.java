package org.blackaddons.platform.mixin.gui;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Lets the storage browser move real slots under its grid, so native clicks land without a relay. {@code x}/{@code y}
 * are final, hence {@link Mutable}.
 */
@Mixin(Slot.class)
public interface SlotAccessor {
    @Mutable
    @Accessor("x")
    void blackaddons$setX(int x);

    @Mutable
    @Accessor("y")
    void blackaddons$setY(int y);
}
