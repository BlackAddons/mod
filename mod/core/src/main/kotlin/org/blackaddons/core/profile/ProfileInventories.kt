package org.blackaddons.core.profile

import org.blackaddons.api.Platform
import org.blackaddons.api.skyblock.profile.ProfileInventory
import org.blackaddons.api.skyblock.profile.ProfileMember
import org.blackaddons.api.util.concurrent.OffThread
import java.util.WeakHashMap
import java.util.concurrent.CompletableFuture

/**
 * Decodes a member's inventories the first time they're asked for and keeps them with the member. The Profile Viewer
 * starts the decode as soon as a profile is shown, so the Inventory tab usually finds it done.
 */
internal object ProfileInventories {

    private val EMPTY = ProfileInventory(
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
    )

    // Keyed by the member object. A new lookup creates new members, so a stale inventory goes away with its old member.
    private val decoded = WeakHashMap<ProfileMember, CompletableFuture<ProfileInventory>>()

    fun load(member: ProfileMember, onResult: (ProfileInventory) -> Unit) {
        val (task, started) = synchronized(decoded) {
            val existing = decoded[member]
            if (existing != null) existing to false
            else CompletableFuture<ProfileInventory>().also { decoded[member] = it } to true
        }
        // Not on ProfileWorkers: the decode waits on its blobs there, and a full pool would end up waiting on itself.
        if (started) OffThread.run(
            name = "profile-inventory",
            work = { decode(member) },
            onFailure = { EMPTY },
            onResult = { task.complete(it) },
        )
        task.thenAccept { inventory -> Platform.screenHost.onClientThread { onResult(inventory) } }
    }

    private fun decode(member: ProfileMember): ProfileInventory {
        val started = System.nanoTime()
        return RawHypixelParser.inventory(member.raw).also {
            LoadTrace.write("inventory of ${member.uuid}: decoded in ${LoadTrace.ms(System.nanoTime() - started)} ms")
        }
    }
}
