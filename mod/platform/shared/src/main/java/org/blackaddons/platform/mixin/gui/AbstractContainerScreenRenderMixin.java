package org.blackaddons.platform.mixin.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.blackaddons.platform.impl.gui.MinecraftContainerOverlays;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hides the vanilla container GUI under a storage overlay. The world background is extracted inside extractContents, so
 * the rest of that call gets cancelled right after. Carried item and tooltip are skipped too. The overlay redraws what
 * it needs.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenRenderMixin {

    @Shadow
    protected @Nullable Slot hoveredSlot;

    @Shadow
    private @Nullable Slot getHoveredSlot(double x, double y) {
        throw new AssertionError();
    }

    @Shadow
    private void onStopHovering(Slot slot) {
        throw new AssertionError();
    }

    @Unique
    private boolean blackaddons$hidden() {
        return MinecraftContainerOverlays.INSTANCE.suppressesVanilla((AbstractContainerScreen<?>) (Object) this);
    }

    @Inject(
        method = "extractContents",
        at = @At(value = "INVOKE", shift = At.Shift.AFTER,
                 target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"),
        cancellable = true)
    private void blackaddons$hideContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        if (!blackaddons$hidden()) return;
        // The cancelled rest also tracks the hovered slot, which number-key swap, Q-drop and mod slot locks read.
        Slot previous = this.hoveredSlot;
        this.hoveredSlot = this.getHoveredSlot(mouseX, mouseY);
        if (previous != null && previous != this.hoveredSlot) this.onStopHovering(previous);
        ci.cancel();
    }

    @Inject(method = "extractCarriedItem", at = @At("HEAD"), cancellable = true)
    private void blackaddons$hideCarried(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (blackaddons$hidden()) ci.cancel();
    }

    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
    private void blackaddons$hideTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (blackaddons$hidden()) ci.cancel();
    }

    /**
     * The overlay parks real slots under its grid, possibly outside the chest rect. Vanilla would treat clicks there as
     * outside and drop the item.
     */
    @Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
    private void blackaddons$neverOutside(double mx, double my, int xo, int yo, CallbackInfoReturnable<Boolean> cir) {
        if (blackaddons$hidden()) cir.setReturnValue(false);
    }
}
