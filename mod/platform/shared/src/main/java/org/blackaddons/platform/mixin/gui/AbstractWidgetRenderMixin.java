package org.blackaddons.platform.mixin.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import org.blackaddons.platform.impl.gui.TitleMenu;
import org.blackaddons.platform.impl.gui.TitleQuickToggles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// After the hover flag is set, so our own drawing can still read it. Skips vanilla's look and tooltip.
@Mixin(AbstractWidget.class)
public abstract class AbstractWidgetRenderMixin {

    @Inject(
            method = "extractRenderState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/AbstractWidget;extractWidgetRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"),
            cancellable = true
    )
    private void blackaddons$restyled(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        AbstractWidget widget = (AbstractWidget) (Object) this;
        if (TitleMenu.owns(widget) || TitleQuickToggles.owns(widget)) ci.cancel();
    }
}
