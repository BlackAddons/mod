package org.blackaddons.platform.impl.world
import net.minecraft.client.Minecraft
import org.blackaddons.api.world.ClientLighting

/** For the light-engine mixins: is this the client's query, with [ClientLighting] off? */
object ClientLightGate {

    @JvmStatic
    fun skips(): Boolean {
        if (!ClientLighting.disabled) return false
        val mc = Minecraft.getInstance()
        return mc.singleplayerServer == null || mc.isSameThread
    }
}
