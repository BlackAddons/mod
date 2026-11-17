package org.blackaddons.api.event.world

import org.blackaddons.api.event.Event
import org.blackaddons.api.world.ItemInfo

/**
 * The server changed the items of a menu, after the client has applied it. [containerId] 0 is your own inventory;
 * otherwise it is the open container (`Platform.container.interactionId()`). Slot numbers are the menu's own, the
 * container first and your inventory after it. Client thread.
 */
sealed class ContainerUpdateEvent(val containerId: Int) : Event() {

    val isPlayerInventory: Boolean get() = containerId == 0

    /** Every slot at once, as when a menu opens or Hypixel redraws it. Built on first read. */
    class Content(containerId: Int, items: () -> List<ItemInfo?>) : ContainerUpdateEvent(containerId) {
        val items: List<ItemInfo?> by lazy(items)
    }

    /** One slot changed. */
    class Slot(containerId: Int, val slot: Int, item: () -> ItemInfo?) : ContainerUpdateEvent(containerId) {
        val item: ItemInfo? by lazy(item)
    }
}
