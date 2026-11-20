package org.blackaddons.platform.mixin.gui;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** {@code topPos}/{@code leftPos}/image size for {@code MinecraftContainerOverlays}, {@code hoveredSlot} for the dev copy tool. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("topPos")
    int blackaddons$getTopPos();

    @Accessor("topPos")
    void blackaddons$setTopPos(int topPos);

    @Accessor("leftPos")
    int blackaddons$getLeftPos();

    @Accessor("leftPos")
    void blackaddons$setLeftPos(int leftPos);

    @Accessor("imageWidth")
    int blackaddons$getImageWidth();

    @Accessor("imageHeight")
    int blackaddons$getImageHeight();

    @Accessor("hoveredSlot")
    Slot blackaddons$getHoveredSlot();
}
