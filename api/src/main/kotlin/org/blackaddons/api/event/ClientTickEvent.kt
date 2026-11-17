package org.blackaddons.api.event

sealed class ClientTickEvent : Event() {
    object Pre : ClientTickEvent()
    object Post : ClientTickEvent()
}
