package org.blackaddons.platform.impl

import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.EventListener
import org.blackaddons.api.event.EventPriority
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.host.GameStats
import org.blackaddons.core.tps.TpsTracker
import org.blackaddons.platform.Installable
import org.blackaddons.platform.event.impl.PacketEvent
import org.blackaddons.platform.impl.MinecraftGameStats.PING_INTERVAL_TICKS
import org.blackaddons.platform.impl.MinecraftGameStats.install
import org.blackaddons.platform.impl.MinecraftGameStats.ping
import java.util.concurrent.ConcurrentHashMap

/**
 * [GameStats] from the client. [ping] is measured with vanilla ping/pong every [PING_INTERVAL_TICKS] (like F3), since
 * Hypixel zeroes tab-list latency on Skyblock. Call [install] once at startup.
 */
object MinecraftGameStats : GameStats, Installable {

    private val mc get() = Minecraft.getInstance()

    /** ~4s at 20 tps. */
    private const val PING_INTERVAL_TICKS = 80

    /** Give up on a missing pong so the loop resumes. */
    private const val PING_TIMEOUT_MS = 10_000L

    // Written on netty (pong) and client (send, reset) threads, read on render.
    @Volatile private var currentPing = 0
    @Volatile private var awaitingPong = false
    @Volatile private var lastPacketReceivedAtMs = 0L
    private var ticksSincePing = 0
    private var pingSentAtMs = 0L
    private val activePings = ConcurrentHashMap<Long, (Int) -> Unit>()

    override val fps: Int get() = mc.fps

    override val ping: Int get() = currentPing

    override val tps: Double? get() = TpsTracker.current()

    override val lastPacketTimeMs: Long get() = lastPacketReceivedAtMs

    override fun install() {
        EventListener(ClientTickEvent.Post::class.java, EventPriority.NORMAL) {
            if (awaitingPong && System.currentTimeMillis() - pingSentAtMs > PING_TIMEOUT_MS) awaitingPong = false
            if (++ticksSincePing >= PING_INTERVAL_TICKS) {
                ticksSincePing = 0
                sendPing()
            }

            val now = System.currentTimeMillis()
            activePings.keys.removeIf { sentTime ->
                if (now - sentTime > PING_TIMEOUT_MS) {
                    val callback = activePings[sentTime]
                    if (callback != null) {
                        mc.execute { callback(-1) }
                    }
                    true
                } else {
                    false
                }
            }
        }.register()

        EventListener(PacketEvent.S2C.Pre::class.java, EventPriority.HIGHEST) {
            lastPacketReceivedAtMs = System.currentTimeMillis()
            val packet = event.packet
            if (packet is ClientboundPongResponsePacket) {
                val sentTime = packet.time
                val latency = calculateLatency(sentTime)
                if (sentTime == pingSentAtMs) {
                    currentPing = latency
                    awaitingPong = false
                }
                val callback = activePings.remove(sentTime)
                if (callback != null) {
                    mc.execute { callback(latency) }
                }
            }
        }.register()

        EventListener(WorldChangeEvent::class.java, EventPriority.NORMAL) {
            TpsTracker.reset()
            currentPing = 0
            ticksSincePing = 0
            awaitingPong = false
            lastPacketReceivedAtMs = 0L
            activePings.values.forEach { callback ->
                mc.execute { callback(-1) }
            }
            activePings.clear()
        }.register()
    }

    private fun sendPing() {
        if (awaitingPong) return
        val connection = mc.connection ?: return
        if (mc.player == null) return
        awaitingPong = true
        pingSentAtMs = System.currentTimeMillis()
        connection.send(ServerboundPingRequestPacket(pingSentAtMs))
    }

    override fun measureActivePing(callback: (Int) -> Unit) {
        val connection = mc.connection
        if (connection == null || mc.player == null) {
            callback(-1)
            return
        }
        var sentTime = System.currentTimeMillis()
        while (activePings.containsKey(sentTime) || sentTime == pingSentAtMs) {
            sentTime++
        }
        activePings[sentTime] = callback
        connection.send(ServerboundPingRequestPacket(sentTime))
    }

    private fun calculateLatency(sentTime: Long): Int =
        (System.currentTimeMillis() - sentTime).coerceAtLeast(0L).toInt()
}
