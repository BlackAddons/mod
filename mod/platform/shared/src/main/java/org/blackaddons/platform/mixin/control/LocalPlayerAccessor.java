package org.blackaddons.platform.mixin.control;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LocalPlayer.class)
public interface LocalPlayerAccessor {

    @Accessor("lastSentInput")
    void blackaddons$setLastSentInput(Input input);
}
