package org.blackaddons.platform.event.impl

import net.minecraft.network.protocol.Packet
import org.blackaddons.api.event.Cancellable
import org.blackaddons.api.event.Event

/**
 * Raw packets. **Check [packet]'s type before cancelling**: an arbitrary S2C cancel can break login or
 * keepalive (kick after ~30s) or freeze world load; C2S can desync position, inventory or block state.
 *
 * [S2C.Pre] fires once per top-level packet on netty. [S2C.Post] only for packets handled on netty (ones
 * handed to the client thread leave by exception). C2S fires on the sending thread.
 */
sealed class PacketEvent(open val packet: Packet<*>) : Event(), Cancellable {
    sealed class S2C(packet: Packet<*>) : PacketEvent(packet) {
        class Pre(packet: Packet<*>, override var cancelled: Boolean = false) : S2C(packet)
        class Post(packet: Packet<*>, override var cancelled: Boolean = false) : S2C(packet)
    }
    sealed class C2S(packet: Packet<*>) : PacketEvent(packet) {
        class Pre(packet: Packet<*>, override var cancelled: Boolean = false) : C2S(packet)
        class Post(packet: Packet<*>, override var cancelled: Boolean = false) : C2S(packet)
    }
}
