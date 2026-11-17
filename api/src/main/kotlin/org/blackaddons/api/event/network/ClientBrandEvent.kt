package org.blackaddons.api.event.network

import org.blackaddons.api.event.Event

/** The brand sent in `minecraft:brand` (Fabric: `"fabric"`). Set [brand] to override it; null keeps the default. */
class ClientBrandEvent(val vanillaBrand: String) : Event() {
    var brand: String? = null
}
