package org.blackaddons.core.cosmetics.me
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.blackaddons.api.cosmetics.NickFont
import org.blackaddons.api.util.io.parseJsonObject
import org.blackaddons.core.backend.Backend
import org.blackaddons.core.backend.Http
import org.blackaddons.core.cosmetics.store.CosmeticJson

internal object MeCosmeticService {

    sealed interface Fetch {
        data class Loaded(val draft: CosmeticDraft) : Fetch
        /** 404: nothing purchased. */
        object NotOwned : Fetch
        /** 400: no linked Minecraft account. */
        object NotLinked : Fetch
        data class Error(val message: String?) : Fetch
    }

    sealed interface Save {
        object Ok : Save
        /** 403: disabled by an admin. */
        object Disabled : Save
        object NotOwned : Save
        /** 400: no linked account or invalid body. */
        data class Invalid(val message: String?) : Save
        data class Error(val message: String?) : Save
    }

    sealed interface FontChange {
        /** Null means back to the game's font. */
        data class Applied(val font: NickFont?) : FontChange
        data class Refused(val message: String?) : FontChange
    }

    /** Worn right away, without review. The backend fetches the font's metadata, so this blocks for a moment. */
    fun setFont(id: String): FontChange {
        val body = JsonObject().apply { addProperty("id", id) }
        val response = Http.send(
            Backend.ME_COSMETIC_URL + "/font", method = Http.Method.PUT, body = body.toString(),
            contentType = "application/json", authenticated = true,
        ) ?: return FontChange.Refused(null)
        if (response.status != 200) return FontChange.Refused(response.error)
        val font = runCatching { CosmeticJson.parseFont(JsonParser.parseString(response.body).asJsonObject) }.getOrNull()
        return font?.let(FontChange::Applied) ?: FontChange.Refused(null)
    }

    fun clearFont(): FontChange {
        val response = Http.send(Backend.ME_COSMETIC_URL + "/font", method = Http.Method.DELETE, authenticated = true)
            ?: return FontChange.Refused(null)
        return if (response.status == 204) FontChange.Applied(null) else FontChange.Refused(response.error)
    }

    sealed interface NameChange {
        data class Applied(val name: String) : NameChange
        data class Pending(val ticketId: Long) : NameChange
        data class Refused(val message: String?) : NameChange
    }

    fun changeName(name: String): NameChange {
        val body = JsonObject().apply { addProperty("name", name) }
        val response = Http.send(
            Backend.ME_COSMETIC_URL + "/name",
            method = Http.Method.PUT,
            body = body.toString(),
            contentType = "application/json",
            authenticated = true,
        ) ?: return NameChange.Refused(null)

        if (response.status != 200) return NameChange.Refused(response.error)
        val json = parseJsonObject(response.body)
            ?: return NameChange.Refused(null)
        val status = json.get("status")?.takeIf { !it.isJsonNull }?.asString
        val ticket = json.get("ticketId")?.takeIf { !it.isJsonNull }?.asLong
        return when {
            status == "pending" && ticket != null -> NameChange.Pending(ticket)
            else -> NameChange.Applied(json.get("name")?.takeIf { !it.isJsonNull }?.asString ?: name)
        }
    }

    fun fetch(): Fetch {
        val response = Http.get(Backend.ME_COSMETIC_URL, authenticated = true)
            ?: return Fetch.Error("not authenticated")

        return when (response.status) {
            200 -> runCatching<Fetch> { Fetch.Loaded(CosmeticDraft.fromDto(JsonParser.parseString(response.body).asJsonObject)) }
                .getOrElse { Fetch.Error(it.toString()) }
            404 -> Fetch.NotOwned
            400 -> Fetch.NotLinked
            else -> Fetch.Error("HTTP ${response.status}")
        }
    }

    fun save(draft: CosmeticDraft): Save {
        val response = Http.send(
            Backend.ME_COSMETIC_URL,
            method = Http.Method.PUT,
            body = draft.toUpdateJson().toString(),
            contentType = "application/json",
            authenticated = true,
        ) ?: return Save.Error("not authenticated")

        return when (response.status) {
            204 -> Save.Ok
            403 -> Save.Disabled
            404 -> Save.NotOwned
            400 -> Save.Invalid(response.error)
            else -> Save.Error("HTTP ${response.status}")
        }
    }
}
