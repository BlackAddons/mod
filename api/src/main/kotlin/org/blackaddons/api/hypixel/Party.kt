package org.blackaddons.api.hypixel

import org.blackaddons.api.HostApi
import org.blackaddons.api.Platform
import java.util.UUID

/**
 * Your Hypixel party, from the server's own party list, asked again whenever a party notice shows in chat. Empty
 * when you are not in a party, off Hypixel, or before the server has answered. Changes arrive as
 * `org.blackaddons.api.event.hypixel.PartyChangeEvent`. Read it on the client thread.
 */
object Party {

    @Volatile
    private var bound: PartyProvider? = null

    private val provider: PartyProvider
        get() = bound ?: error("Party was read before the host bound its provider")

    /** Host only. */
    @HostApi
    fun bind(provider: PartyProvider) {
        synchronized(this) {
            bound?.let { error("Party is already bound to ${it::class.java.name}") }
            bound = provider
        }
    }

    /** Everyone in the party, you included. */
    val members: List<PartyMember> get() = provider.members

    val inParty: Boolean get() = members.isNotEmpty()

    val leader: PartyMember? get() = members.firstOrNull { it.role == PartyRole.LEADER }

    /** You lead the party. */
    val isLeader: Boolean get() = leader?.uuid?.let { it == Platform.player.uuid } == true

    /** Everyone but you. */
    val others: List<PartyMember> get() = members.filter { it.uuid != Platform.player.uuid }

    /** By account name, ignoring case. */
    fun member(name: String): PartyMember? = members.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** Asks the server again. Hypixel rate-limits the request, so a burst of calls sends one. */
    fun refresh() = provider.refresh()
}

/** [name] is null until the account is resolved, which can take a moment for someone never seen before. */
data class PartyMember(val uuid: UUID, val role: PartyRole, val name: String? = null)

enum class PartyRole { LEADER, MODERATOR, MEMBER }

/** Host side of [Party], bound once. */
interface PartyProvider {
    val members: List<PartyMember>
    fun refresh()
}
