package org.blackaddons.platform.mixin.network;

import net.minecraft.client.ClientBrandRetriever;
import org.blackaddons.api.event.EventBus;
import org.blackaddons.api.event.network.ClientBrandEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientBrandRetriever.class)
public class ClientBrandRetrieverMixin {

    @Inject(method = "getClientModName", at = @At("HEAD"), cancellable = true)
    private static void blackaddons$brand(CallbackInfoReturnable<String> cir) {
        ClientBrandEvent event = EventBus.INSTANCE.post(new ClientBrandEvent(ClientBrandRetriever.VANILLA_NAME));
        String brand = event.getBrand();
        if (brand != null) cir.setReturnValue(brand);
    }
}
