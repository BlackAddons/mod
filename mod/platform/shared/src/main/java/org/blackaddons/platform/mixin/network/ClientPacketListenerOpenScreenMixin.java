package org.blackaddons.platform.mixin.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.blackaddons.api.event.EventBus;
import org.blackaddons.api.event.world.ContainerOpenEvent;
import org.blackaddons.platform.impl.gui.HeadlessMenus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Posts {@code ContainerOpenEvent} on the client thread before the screen gets created. A cancelled event is answered
 * with the close packet the server would get from closing the screen, and a headless one gets its menu without a
 * screen.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerOpenScreenMixin {

    @Inject(
        method = "handleOpenScreen",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread",
            shift = At.Shift.AFTER
        ),
        cancellable = true
    )
    private void blackaddons$openScreen(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        String menuType = String.valueOf(BuiltInRegistries.MENU.getKey(packet.getType()));
        String title = packet.getTitle().getString();
        ContainerOpenEvent event = EventBus.INSTANCE.post(new ContainerOpenEvent(title, menuType, false));
        if (event.getCancelled()) {
            ((ClientPacketListener) (Object) this).send(new ServerboundContainerClosePacket(packet.getContainerId()));
            ci.cancel();
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (!event.getHeadless() || player == null) return;
        AbstractContainerMenu menu = packet.getType().create(packet.getContainerId(), player.getInventory());
        player.containerMenu = menu;
        HeadlessMenus.INSTANCE.adopt(menu, title);
        ci.cancel();
    }
}
