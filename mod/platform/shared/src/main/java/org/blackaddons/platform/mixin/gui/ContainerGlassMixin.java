package org.blackaddons.platform.mixin.gui;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.blackaddons.platform.ui.skin.ContainerGlass;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** The parts of container glass every container shares; each applies only to a screen {@link ContainerGlass} painted. */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerGlassMixin {

    @Shadow
    protected @Nullable Slot hoveredSlot;

    @Unique
    private AbstractContainerScreen<?> blackaddons$self() {
        return (AbstractContainerScreen<?>) (Object) this;
    }

    @ModifyArg(
        method = "extractLabels",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"),
        index = 4)
    private int blackaddons$labelColor(int color) {
        return ContainerGlass.INSTANCE.labelColor(blackaddons$self(), color);
    }

    @ModifyArg(
        method = {"extractSlotHighlightBack", "extractSlotHighlightFront"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite"),
        index = 4)
    private int blackaddons$hideHighlight(int width) {
        return ContainerGlass.INSTANCE.skinned(blackaddons$self()) ? 0 : width;
    }

    @ModifyExpressionValue(
        method = "extractSlot",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/world/inventory/Slot;getItem()Lnet/minecraft/world/item/ItemStack;",
                 ordinal = 0))
    private ItemStack blackaddons$hideFiller(ItemStack original, @Local(argsOnly = true) Slot slot) {
        return ContainerGlass.INSTANCE.hides(blackaddons$self(), slot) ? ItemStack.EMPTY : original;
    }

    @ModifyExpressionValue(
        method = "extractTooltip",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/Slot;hasItem()Z"))
    private boolean blackaddons$noFillerTooltip(boolean hasItem) {
        return hasItem && !ContainerGlass.INSTANCE.hides(blackaddons$self(), hoveredSlot);
    }
}
