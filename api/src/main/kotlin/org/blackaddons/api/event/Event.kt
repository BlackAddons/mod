package org.blackaddons.api.event

abstract class Event

interface Cancellable {
    var cancelled: Boolean
}
