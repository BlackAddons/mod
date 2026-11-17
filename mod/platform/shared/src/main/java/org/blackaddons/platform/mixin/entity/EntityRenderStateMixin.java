package org.blackaddons.platform.mixin.entity;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.blackaddons.platform.cosmetics.BlackaddonsRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.UUID;

/** Carries the entity UUID on its render state for {@link AvatarRendererMixin}; {@code scale} gets only the state. */
@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateMixin implements BlackaddonsRenderState {

    @Unique private UUID blackaddons$uuid;

    @Override
    public UUID getBlackaddonsUuid() {
        return blackaddons$uuid;
    }

    @Override
    public void setBlackaddonsUuid(UUID uuid) {
        this.blackaddons$uuid = uuid;
    }
}
