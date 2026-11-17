package org.blackaddons.core.profile

import org.blackaddons.api.Platform
import org.blackaddons.api.host.AccountLookup
import org.blackaddons.api.host.MojangAccounts
import org.blackaddons.api.util.Uuids
import org.blackaddons.api.util.concurrent.OffThread
import java.util.UUID

internal object CoreMojangAccounts : MojangAccounts {

    override fun byName(name: String, onResult: (AccountLookup) -> Unit) {
        PlayerNames.sighting(name)?.let { seen ->
            Platform.screenHost.onClientThread { onResult(AccountLookup.Found(seen.uuid, seen.name)) }
            return
        }
        lookUp(onResult) { MojangResolver.resolve(name) }
    }

    override fun byUuid(uuid: UUID, onResult: (AccountLookup) -> Unit) = lookUp(onResult) { MojangResolver.nameOf(uuid) }

    private fun lookUp(onResult: (AccountLookup) -> Unit, resolve: () -> MojangResolver.Resolution) = OffThread.run(
        name = "mojang-lookup",
        work = { answer(resolve()) },
        onFailure = { AccountLookup.Unavailable },
        onResult = { lookup -> Platform.screenHost.onClientThread { onResult(lookup) } },
    )

    private fun answer(resolution: MojangResolver.Resolution): AccountLookup = when (resolution) {
        is MojangResolver.Resolution.Found ->
            Uuids.parse(resolution.id.uuid)?.let { AccountLookup.Found(it, resolution.id.name) } ?: AccountLookup.Unavailable
        MojangResolver.Resolution.NotFound -> AccountLookup.NotFound
        MojangResolver.Resolution.Unavailable -> AccountLookup.Unavailable
    }
}
