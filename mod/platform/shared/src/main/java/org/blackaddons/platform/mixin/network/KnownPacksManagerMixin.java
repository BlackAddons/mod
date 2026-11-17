package org.blackaddons.platform.mixin.network;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.KnownPacksManager;
import net.minecraft.server.packs.repository.KnownPack;
import org.blackaddons.api.event.EventBus;
import org.blackaddons.api.event.network.KnownPackEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;

/**
 * Reports each "known packs" entry as a {@link KnownPackEvent}. A hidden pack's {@code Map<KnownPack, String>} lookup
 * in {@link KnownPacksManager#trySelectingPacks} answers null.
 */
@Mixin(KnownPacksManager.class)
public class KnownPacksManagerMixin {

    @WrapOperation(
        method = "trySelectingPacks",
        at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;")
    )
    private Object blackaddons$filterKnownPack(Map<KnownPack, String> instance, Object key, Operation<Object> original) {
        KnownPack pack = (KnownPack) key;
        KnownPackEvent event = EventBus.INSTANCE.post(
            new KnownPackEvent(pack.namespace(), pack.id(), pack.version())
        );
        if (event.getHidden()) return null;
        return original.call(instance, pack);
    }
}
