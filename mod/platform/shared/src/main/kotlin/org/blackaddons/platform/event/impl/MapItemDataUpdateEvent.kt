package org.blackaddons.platform.event.impl

import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket
import org.blackaddons.api.event.Event

/** After a [ClientboundMapItemDataPacket] has updated the map data. */
class MapItemDataUpdateEvent(val packet: ClientboundMapItemDataPacket) : Event()
