package org.blackaddons.platform.impl.input
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping

object MinecraftKeybinds : AbstractMinecraftKeybinds() {
    override fun registerMapping(mapping: KeyMapping) {
        KeyMappingHelper.registerKeyMapping(mapping)
    }
}
