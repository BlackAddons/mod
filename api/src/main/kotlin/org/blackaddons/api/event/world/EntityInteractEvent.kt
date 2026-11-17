package org.blackaddons.api.event.world

import org.blackaddons.api.event.Cancellable
import org.blackaddons.api.event.Event

/** Local player used or attacked an entity. Fired before anything is sent, so cancelling blocks it. */
sealed class EntityInteractEvent(val entity: EntitySnapshot) : Event(), Cancellable {
    override var cancelled: Boolean = false

    class Use(entity: EntitySnapshot) : EntityInteractEvent(entity)

    class Attack(entity: EntitySnapshot) : EntityInteractEvent(entity)
}
