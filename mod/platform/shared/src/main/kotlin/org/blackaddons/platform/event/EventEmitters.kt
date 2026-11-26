package org.blackaddons.platform.event

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.common.ClientboundPingPacket
import net.minecraft.network.protocol.game.ClientboundBossEventPacket
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import net.minecraft.world.BossEvent
import net.minecraft.world.InteractionResult
import org.blackaddons.api.event.*
import org.blackaddons.api.event.chat.ChatSendEvent
import org.blackaddons.api.event.input.MouseClickEvent
import org.blackaddons.api.event.network.ServerJoinEvent
import org.blackaddons.api.event.network.ServerLeaveEvent
import org.blackaddons.api.event.render.RenderHudEvent
import org.blackaddons.api.event.render.RenderWorldEvent
import org.blackaddons.api.event.world.*
import org.blackaddons.api.hud.HudEditorMode
import org.blackaddons.api.ui.render.Render
import org.blackaddons.core.feature.FeatureManager
import org.blackaddons.core.log.Log
import org.blackaddons.platform.compat.currentScreen
import org.blackaddons.platform.compat.hudHidden
import org.blackaddons.platform.compat.registerWorldPass
import org.blackaddons.platform.event.impl.PacketEvent
import org.blackaddons.platform.event.impl.RenderOverlayEvent
import org.blackaddons.platform.event.impl.ScreenEvent
import org.blackaddons.platform.impl.chat.MinecraftChat
import org.blackaddons.platform.impl.player.MinecraftInteraction
import org.blackaddons.platform.impl.world.EntitySnapshots
import org.blackaddons.platform.impl.world.EntityView
import org.blackaddons.platform.impl.world.MinecraftWorldRenderer
import java.util.UUID

/**
 * Wires Fabric callbacks into [EventBus]. Mixin-emitted events live in `org.blackaddons.platform.mixin`. Also installs
 * the HUD renderer walking every [org.blackaddons.api.hud.HudElement], fed by `GuiMixin`.
 */
object EventEmitters {
    fun init() {
        ClientTickEvents.START_CLIENT_TICK.register { EventBus.post(ClientTickEvent.Pre) }
        ClientTickEvents.END_CLIENT_TICK.register { EventBus.post(ClientTickEvent.Post) }

        emitServerTicks()
        emitBossBars()

        EventListener(PacketEvent.C2S.Pre::class.java, EventPriority.LOWEST) {
            if (event.packet is ServerboundContainerClosePacket && EventBus.post(InventoryEvent.Close()).cancelled) {
                event.cancelled = true
            }
            if (!event.cancelled && MinecraftChat.hold(event.packet)) event.cancelled = true
        }.register()

        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, _ -> EventBus.post(WorldChangeEvent) }
        ClientPlayConnectionEvents.JOIN.register { _, _, client -> EventBus.post(ServerJoinEvent(client.currentServer?.ip)) }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            EventBus.post(ServerLeaveEvent)
            EventBus.post(WorldChangeEvent)
        }

        ClientChunkEvents.CHUNK_LOAD.register { _, chunk -> EventBus.post(ChunkEvent.Load(chunk.pos.x(), chunk.pos.z())) }
        ClientChunkEvents.CHUNK_UNLOAD.register { _, chunk -> EventBus.post(ChunkEvent.Unload(chunk.pos.x(), chunk.pos.z())) }

        /*
         * Fabric splits a send into ALLOW (can block) and MODIFY (can rewrite). Post in ALLOW, stash, apply rewrites in
         * MODIFY. Chat and commands never interleave, so one slot is enough.
         */
        var pendingSend: ChatSendEvent? = null
        ClientSendMessageEvents.ALLOW_CHAT.register { message ->
            val e = EventBus.post(ChatSendEvent(message, isCommand = false))
            pendingSend = e
            !e.cancelled
        }
        ClientSendMessageEvents.MODIFY_CHAT.register { message ->
            pendingSend?.message?.also { pendingSend = null } ?: message
        }
        ClientSendMessageEvents.ALLOW_COMMAND.register { command ->
            val e = EventBus.post(ChatSendEvent(command, isCommand = true))
            pendingSend = e
            !e.cancelled
        }
        ClientSendMessageEvents.MODIFY_COMMAND.register { command ->
            pendingSend?.message?.also { pendingSend = null } ?: command
        }

        /*
         * Resize reuses the Screen via init(), so a per-tick identity check only sees real open/close. Posts both
         * ScreenEvent and the neutral GuiEvent.
         */
        var lastScreen: Screen? = null
        ClientTickEvents.END_CLIENT_TICK.register {
            val current = Minecraft.getInstance().currentScreen
            if (current === lastScreen) return@register
            lastScreen?.let { old ->
                EventBus.post(ScreenEvent.Close(old))
                EventBus.post(GuiEvent.Close(old.title.string, old is AbstractContainerScreen<*>))
            }
            current?.let { now ->
                EventBus.post(ScreenEvent.Open(now))
                EventBus.post(GuiEvent.Open(now.title.string, now is AbstractContainerScreen<*>))
            }
            lastScreen = current
        }

        // Fabric fires on both sides, we want client only. Cancel maps to FAIL, otherwise PASS.
        UseBlockCallback.EVENT.register { _, level, _, hit ->
            if (!level.isClientSide) return@register InteractionResult.PASS
            val pos = hit.blockPos
            if (EventBus.post(BlockInteractEvent.Use(pos.x, pos.y, pos.z, MinecraftInteraction.consumeSyntheticUse())).cancelled) {
                InteractionResult.FAIL
            } else {
                InteractionResult.PASS
            }
        }
        AttackBlockCallback.EVENT.register { _, level, _, pos, _ ->
            if (!level.isClientSide) return@register InteractionResult.PASS
            if (EventBus.post(BlockInteractEvent.Attack(pos.x, pos.y, pos.z)).cancelled) {
                InteractionResult.FAIL
            } else {
                InteractionResult.PASS
            }
        }
        UseEntityCallback.EVENT.register { _, level, _, entity, _ ->
            if (!level.isClientSide) return@register InteractionResult.PASS
            if (EventBus.post(EntityInteractEvent.Use(EntitySnapshots.of(entity))).cancelled) {
                InteractionResult.FAIL
            } else {
                InteractionResult.PASS
            }
        }
        AttackEntityCallback.EVENT.register { _, level, _, entity, _ ->
            if (!level.isClientSide) return@register InteractionResult.PASS
            if (EventBus.post(EntityInteractEvent.Attack(EntitySnapshots.of(entity))).cancelled) {
                InteractionResult.FAIL
            } else {
                InteractionResult.PASS
            }
        }

        // Stage and submission differ per version, see `registerWorldPass` in each McCompat.
        registerWorldPass { ctx ->
            if (EntityView.rendering) return@registerWorldPass
            MinecraftWorldRenderer.beginFrame(ctx)
            try {
                val tickDelta = Minecraft.getInstance().deltaTracker.getGameTimeDeltaPartialTick(false)
                EventBus.post(RenderWorldEvent(tickDelta))
            } finally {
                MinecraftWorldRenderer.endFrame()
            }
        }

        EventListener(RenderOverlayEvent::class.java, EventPriority.NORMAL) {
            val editor = HudEditorMode.active
            if (Minecraft.getInstance().hudHidden && !editor) return@EventListener
            val tickDelta = event.tickDelta
            // BlackSkija is immediate-mode: reset the transform per pass, flushed once per frame. Addons draw first.
            Render.beginPass()
            EventBus.post(RenderHudEvent(tickDelta))
            /**
             * Per-HUD try so one bad draw doesn't break the rest or count toward auto-unregistering this listener. The
             * HUD editor draws its own copies.
             */
            if (!editor) {
                for (hud in FeatureManager.hudElements) {
                    try {
                        Render.isolated { hud.renderElement(false) }
                    } catch (t: Throwable) {
                        Log.main.error("HUD '${hud.name}' threw during render", t)
                    }
                }
            }
        }.register()

        // Reachable HUD elements get the press first, never also the screen behind. Last drawn is on top.
        EventListener(MouseClickEvent::class.java, EventPriority.HIGHEST) {
            if (!event.isPress) return@EventListener
            for (hud in FeatureManager.hudElements.asReversed()) {
                // One listener routes every HUD. EventBus drops listeners that keep throwing.
                val took = try {
                    hud.offerClick(event.button)
                } catch (t: Throwable) {
                    Log.main.error("HUD '${hud.name}' threw handling a click", t)
                    false
                }
                if (took) {
                    event.cancelled = true
                    return@EventListener
                }
            }
        }.register()
    }

    /**
     * Server keep-alive as [ServerTickEvent]: one per tick, and it stops while the server stalls. Derived from the
     * existing packet hook instead of another mixin. Skips `id == 0` like NoammAddons, so one-off pings don't count
     * (our latency check uses `ClientboundPongResponsePacket`). Hops to the client thread since listeners read the
     * world. [PacketEvent.S2C.Pre], not Post: the ping moves to the client thread mid-dispatch and never reaches Post.
     */
    private fun emitServerTicks() {
        EventListener(PacketEvent.S2C.Pre::class.java, EventPriority.NORMAL) {
            val ping = event.packet as? ClientboundPingPacket ?: return@EventListener
            if (ping.id == 0) return@EventListener
            Minecraft.getInstance().execute { EventBus.post(ServerTickEvent) }
        }.register()
    }

    /** Updates carry no name and renames carry no progress, so we keep the last of each per bar. Client thread. */
    private fun emitBossBars() {
        val names = HashMap<UUID, String>()
        val progresses = HashMap<UUID, Float>()
        val handler = object : ClientboundBossEventPacket.Handler {
            override fun add(
                id: UUID, name: Component, progress: Float, color: BossEvent.BossBarColor,
                overlay: BossEvent.BossBarOverlay, darkenScreen: Boolean, playMusic: Boolean, createWorldFog: Boolean,
            ) = update(id, name.string, progress)

            override fun updateName(id: UUID, name: Component) = update(id, name.string, null)

            override fun updateProgress(id: UUID, progress: Float) {
                names[id]?.let { update(id, it, progress) }
            }

            override fun remove(id: UUID) {
                names.remove(id)
                progresses.remove(id)
                EventBus.post(BossBarEvent.Remove(id))
            }

            private fun update(id: UUID, name: String, progress: Float?) {
                names[id] = name
                progress?.let { progresses[id] = it }
                EventBus.post(BossBarEvent.Update(id, name, progresses[id] ?: 1f))
            }
        }
        EventListener(PacketEvent.S2C.Pre::class.java, EventPriority.NORMAL) {
            val packet = event.packet as? ClientboundBossEventPacket ?: return@EventListener
            Minecraft.getInstance().execute { packet.dispatch(handler) }
        }.register()
        EventBus.subscribe<WorldChangeEvent> {
            names.clear()
            progresses.clear()
        }
    }

}
