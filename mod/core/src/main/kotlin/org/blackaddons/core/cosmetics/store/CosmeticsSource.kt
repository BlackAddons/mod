package org.blackaddons.core.cosmetics.store
import com.google.gson.JsonObject
import java.util.UUID

/** Swappable cosmetics transport (HTTP, file, test double). Calls name exactly the players we want. */
internal interface CosmeticsSource {
    /** Max is the backend's batch limit. Off the client thread, no Minecraft. */
    fun resolve(uuids: List<UUID>): CosmeticsAnswer
}

/** [Later] (we asked too often) and [Failed] (unreachable) mean opposite things to the player. */
internal sealed interface CosmeticsAnswer {

    data class Manifest(val root: JsonObject) : CosmeticsAnswer
    data class Later(val retryAfterSeconds: Int?) : CosmeticsAnswer
    data object Failed : CosmeticsAnswer
}
