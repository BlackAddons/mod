package org.blackaddons.platform.event

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.core.Holder
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.sounds.SoundEvent
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.chat.TitleEvent
import org.blackaddons.api.event.world.ContainerUpdateEvent
import org.blackaddons.api.event.world.SoundPlayEvent
import org.blackaddons.platform.impl.world.MinecraftItems

/**
 * The events `ClientPacketListenerPacketMixin` posts, kept here so the mixin stays a list of hooks. Each runs on the
 * client thread, the `cancel` ones answer whether to drop the packet.
 */
object PacketHooks {

    @JvmStatic
    fun title(text: Component, subtitle: Boolean): Boolean {
        val raw = text.string
        val plain = ChatFormatting.stripFormatting(raw) ?: raw
        val event = if (subtitle) TitleEvent.Subtitle(plain, raw) else TitleEvent.Title(plain, raw)
        return EventBus.post(event).cancelled
    }

    @JvmStatic
    fun sound(packet: ClientboundSoundPacket): Boolean =
        sound(packet.sound, packet.x, packet.y, packet.z, packet.volume, packet.pitch)

    @JvmStatic
    fun entitySound(packet: ClientboundSoundEntityPacket): Boolean {
        val entity = Minecraft.getInstance().level?.getEntity(packet.id) ?: return false
        return sound(packet.sound, entity.x, entity.y, entity.z, packet.volume, packet.pitch)
    }

    private fun sound(sound: Holder<SoundEvent>, x: Double, y: Double, z: Double, volume: Float, pitch: Float): Boolean {
        val id = sound.value().location().toString()
        return EventBus.post(SoundPlayEvent(id, x, y, z, volume, pitch)).cancelled
    }

    @JvmStatic
    fun slot(packet: ClientboundContainerSetSlotPacket) {
        val stack = packet.item
        EventBus.post(ContainerUpdateEvent.Slot(packet.containerId, packet.slot) { MinecraftItems.info(stack) })
    }

    @JvmStatic
    fun content(packet: ClientboundContainerSetContentPacket) {
        val stacks = packet.items()
        EventBus.post(ContainerUpdateEvent.Content(packet.containerId()) { stacks.map(MinecraftItems::info) })
    }
}
