package org.blackaddons.api.event.hypixel

import org.blackaddons.api.event.Event
import org.blackaddons.api.hypixel.PartyMember

/** `Party.members` changed to [members]: someone joined or left, a role changed, or a name was resolved. */
class PartyChangeEvent(val members: List<PartyMember>) : Event()
