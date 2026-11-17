package org.blackaddons.platform.impl.world
/** 26.1 renamed `Level.getDayTime()` to `getOverworldClockTime()`. */
object MinecraftWorld : AbstractMinecraftWorld() {
    override val dayTime: Long get() = mc.level?.overworldClockTime ?: 0L
}
