package org.blackaddons.platform.util

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import java.util.concurrent.CompletableFuture

/**
 * Runs [read] on the client thread and returns its result. Hosts get called from any thread, and reading live
 * entity/chunk storage off-thread fails randomly (CME, mid-swap nulls). `Minecraft.execute` can't return a value, so
 * reads use this. Snapshots stay valid a tick later, so hop instead of refusing.
 *
 * Runs inline when already on the client thread. A caller blocking the client thread on a worker that calls this can
 * still deadlock.
 */
internal fun <T> readOnClient(read: () -> T): T {
    val mc = Minecraft.getInstance()
    if (mc.isSameThread || RenderSystem.isOnRenderThread()) return read()

    /*
     * Not `submit(Supplier)`: Kotlin picks the `Runnable` overload and drops the value. Exceptions have to reach the
     * caller or it waits forever.
     */
    val result = CompletableFuture<T>()
    mc.execute {
        try {
            result.complete(read())
        } catch (t: Throwable) {
            result.completeExceptionally(t)
        }
    }
    return result.join()
}

/** Fire-and-forget counterpart of [readOnClient], inline when already on the client thread. */
internal inline fun onClient(crossinline block: (Minecraft) -> Unit) {
    val mc = Minecraft.getInstance()
    if (mc.isSameThread) block(mc) else mc.execute { block(mc) }
}
