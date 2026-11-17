package org.blackaddons.core.cosmetics.store
import com.google.gson.JsonObject
import org.blackaddons.api.util.Uuids
import org.blackaddons.core.backend.Backend
import org.blackaddons.core.log.Log
import org.blackaddons.core.storage.ServedDocument
import java.util.UUID

/**
 * Name -> UUID of every cosmetic owner, from the backend. Hypixel panels show some players by name only (party in
 * another lobby, offline co-op), so we need this to recognize them. No cosmetic data here, that's in [CosmeticStore].
 */
object CosmeticOwners {

    @Volatile
    private var byName: Map<String, UUID> = emptyMap()

    fun load() = ServedDocument.load(Backend.COSMETIC_OWNERS_URL, "cosmetics/owners") { document ->
        byName = parse(document)
        Log.main.debug("Cosmetic owner directory loaded: {} names", byName.size)
    }

    internal fun known(): Map<String, UUID> = byName

    // Internal for the test.
    internal fun parse(document: JsonObject): Map<String, UUID> {
        val owners = document.getAsJsonArray("owners") ?: return emptyMap()
        val out = HashMap<String, UUID>(owners.size())
        for (element in owners) {
            val entry = element as? JsonObject ?: continue
            val name = entry.get("name")?.takeIf { it.isJsonPrimitive }?.asString ?: continue
            val uuid = Uuids.parse(entry.get("uuid")?.takeIf { it.isJsonPrimitive }?.asString) ?: continue
            if (name.isNotEmpty()) out[name.lowercase()] = uuid
        }
        return out
    }
}
