package org.blackaddons.platform.mixin.control;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import org.blackaddons.api.event.EventBus;
import org.blackaddons.api.event.world.ClickKind;
import org.blackaddons.api.event.world.InventoryEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.1 renamed {@code handleInventoryMouseClick} -> {@code handleContainerInput} and {@code ClickType} -> {@link
 * ContainerInput}. Cancelling suppresses the click on both client and server.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {

    @Inject(method = "handleContainerInput", at = @At("HEAD"), cancellable = true)
    private void blackaddons$onSlotClick(
            int containerId, int slotNum, int buttonNum,
            ContainerInput containerInput, Player player,
            CallbackInfo ci
    ) {
        ClickKind kind = switch (containerInput) {
            case PICKUP -> ClickKind.PICKUP;
            case QUICK_MOVE -> ClickKind.QUICK_MOVE;
            case SWAP -> ClickKind.SWAP;
            case CLONE -> ClickKind.CLONE;
            case THROW -> ClickKind.THROW;
            case QUICK_CRAFT -> ClickKind.QUICK_CRAFT;
            case PICKUP_ALL -> ClickKind.PICKUP_ALL;
        };
        InventoryEvent.SlotClick event = new InventoryEvent.SlotClick(slotNum, buttonNum, kind, false);
        EventBus.INSTANCE.post(event);
        if (event.getCancelled()) ci.cancel();
    }
}
