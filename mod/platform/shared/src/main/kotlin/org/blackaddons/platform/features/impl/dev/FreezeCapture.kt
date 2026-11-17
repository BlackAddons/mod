package org.blackaddons.platform.features.impl.dev

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.minecraft.client.Minecraft
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.util.concurrent.Workers
import org.blackaddons.api.util.dev.DevFiles
import org.blackaddons.core.log.Log
import org.blackaddons.platform.compat.currentOverlay
import java.lang.management.ManagementFactory
import java.lang.management.ThreadInfo
import java.util.concurrent.TimeUnit

/** Captures JVM stacks from a worker while the client thread is stuck. */
object FreezeCapture : Feature(
    name = "Freeze Capture",
    description = "Save thread stacks after a 200 ms client stall to config/blackaddons/dev/freezes.",
) {
    private const val STALL_MS = 200L
    private const val POLL_MS = 50L
    private const val SAMPLE_GAP_MS = 100L

    @Volatile private var lastBeatNs = 0L
    @Volatile private var clientThread: Thread? = null
    @Volatile private var watcher: Thread? = null

    override fun init() {
        register<ClientTickEvent.Post> {
            clientThread = Thread.currentThread()
            lastBeatNs = System.nanoTime()
        }
        ClientLifecycleEvents.CLIENT_STOPPING.register { watcher = null }
    }

    override fun onEnable() {
        lastBeatNs = System.nanoTime()
        watcher = Workers.start("freeze-capture", ::watch)
    }

    override fun onDisable() {
        watcher?.interrupt()
        watcher = null
        clientThread = null
    }

    private fun watch() {
        val self = Thread.currentThread()
        var capturedBeat = -1L
        while (watcher === self) {
            try {
                Thread.sleep(POLL_MS)
                val beat = lastBeatNs
                if (clientThread == null || beat == capturedBeat ||
                    System.nanoTime() - beat < TimeUnit.MILLISECONDS.toNanos(STALL_MS) ||
                    Minecraft.getInstance().currentOverlay != null) continue

                capturedBeat = beat
                val client = clientThread ?: continue
                val started = System.nanoTime()
                val bean = ManagementFactory.getThreadMXBean()
                val report = buildString {
                    appendLine("Client stall at least $STALL_MS ms; render thread: ${client.name} (${client.threadId()})")
                    repeat(3) { sample ->
                        if (sample > 0) Thread.sleep(SAMPLE_GAP_MS)
                        appendLine("\nSample ${sample + 1}, ${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)} ms after detection")
                        val threads = bean.dumpAllThreads(true, true)
                        threads.firstOrNull { it.threadId == client.threadId() }?.let { appendThread(it) }
                        threads.filter { it.threadId != client.threadId() }.forEach { appendThread(it) }
                    }
                }
                val file = DevFiles.writeTimestamped("freezes", "freeze-${System.currentTimeMillis()}", "txt", report)
                Log.main.warn("Client stall captured at {}", file)
            } catch (_: InterruptedException) {
                break
            } catch (t: Throwable) {
                Log.main.error("Could not capture client stall", t)
            }
        }
    }

    private fun StringBuilder.appendThread(info: ThreadInfo) {
        appendLine("\n\"${info.threadName}\" id=${info.threadId} state=${info.threadState} lock=${info.lockName ?: "none"}")
        info.stackTrace.forEach { appendLine("    at $it") }
        info.lockedMonitors.forEach { appendLine("    locked $it") }
        info.lockedSynchronizers.forEach { appendLine("    synchronizer $it") }
    }
}
