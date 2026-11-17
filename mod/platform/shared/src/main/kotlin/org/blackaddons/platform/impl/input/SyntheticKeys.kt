package org.blackaddons.platform.impl.input
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import org.blackaddons.api.input.Keys
import org.blackaddons.api.player.Lease
import org.blackaddons.api.player.LeaseState
import org.blackaddons.platform.compat.NativeKeys
import org.blackaddons.platform.mixin.input.KeyboardHandlerInvoker
import org.blackaddons.platform.util.onClient
import java.util.concurrent.ConcurrentHashMap

/** Keys a feature presses via `KeyboardHandler.keyPress`, so screens, mappings and other mods see a physical press. */
internal object SyntheticKeys {

    // Lets KeyboardHandlerMixin mark our own KeyInputEvent.
    @JvmStatic
    var pressing = false
        private set

    private const val PREFIX = "key.keyboard."

    // Key code -> lease count, so one release doesn't lift someone else's press.
    private val held = ConcurrentHashMap<Int, Int>()

    // Read by each version's InputConstantsMixin.
    @JvmStatic
    fun isHeld(code: Int): Boolean = held.containsKey(code)

    fun press(name: String): Lease? {
        val full = name.trim().lowercase().let { if (it.startsWith("key.")) it else PREFIX + it }
        if (!full.startsWith(PREFIX)) return null
        val code = MinecraftKeyNames.code(full)
        if (code == Keys.UNKNOWN) return null
        held.merge(code, 1, Int::plus)
        onClient { send(code, InputConstants.PRESS) }
        return KeyLease(code)
    }

    private fun send(code: Int, action: Int) {
        val mc = Minecraft.getInstance()
        pressing = true
        try {
            (mc.keyboardHandler as KeyboardHandlerInvoker).`blackaddons$keyPress`(mc.window.handle(), action, NativeKeys.keyEvent(code))
        } finally {
            pressing = false
        }
    }

    private class KeyLease(private val code: Int) : Lease {
        @Volatile
        override var state = LeaseState.ACTIVE
            private set

        override fun release() {
            synchronized(this) {
                if (isEnded) return
                state = LeaseState.RELEASED
            }
            held.computeIfPresent(code) { _, count -> (count - 1).takeIf { it > 0 } }
            onClient { send(code, InputConstants.RELEASE) }
        }
    }
}
