package org.blackaddons.platform.util

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents

/**
 * A value rebuilt at most once per client tick, for Minecraft state that render code reads. Keyed on [ClientTicks], not
 * `gameTime`, which stops in paused single-player. Rebuilt via [readOnClient], since off-thread builds would race the
 * tick.
 */
internal class TickCache<T : Any>(private val build: () -> T) {

    @Volatile private var value: T? = null
    @Volatile private var builtOn = -1L

    fun get(): T {
        val tick = ClientTicks.count
        value?.let { if (builtOn == tick) return it }
        return readOnClient(build).also {
            value = it
            builtOn = tick
        }
    }
}

/** Only compared for equality. */
internal object ClientTicks {

    @Volatile var count = 0L
        private set

    /** Call once at client init. */
    fun start() {
        ClientTickEvents.START_CLIENT_TICK.register { count++ }
    }
}
