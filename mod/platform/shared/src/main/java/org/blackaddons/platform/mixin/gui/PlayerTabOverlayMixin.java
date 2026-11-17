package org.blackaddons.platform.mixin.gui;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import org.blackaddons.platform.cosmetics.NameReplacer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Swaps tab-list names for cosmetic names. Replacing the returned component keeps vanilla's order and team colors. Only
 * at the draw call: mods reading getNameForDisplay on tick flatten our hex colors to literal `<#RRGGBB>`.
 */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {

    @ModifyExpressionValue(
        method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;getNameForDisplay(Lnet/minecraft/client/multiplayer/PlayerInfo;)Lnet/minecraft/network/chat/Component;")
    )
    private Component blackaddons$tabName(Component vanilla) {
        return vanilla == null ? null : NameReplacer.INSTANCE.decorate(vanilla);
    }
}
