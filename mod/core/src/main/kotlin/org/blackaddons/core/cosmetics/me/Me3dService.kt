package org.blackaddons.core.cosmetics.me
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.blackaddons.core.backend.Backend
import org.blackaddons.core.backend.Http
import org.blackaddons.core.cosmetics.mesh.Cosmetic3dEntry
import org.blackaddons.core.cosmetics.mesh.CosmeticAdjust

/** Your 3D cosmetics through `/v1/me/cosmetics3d`. Locked ones are listed, the server decides what you can wear. Blocking. */
internal object Me3dService {

    sealed interface Fetch {
        data class Loaded(val entries: List<Cosmetic3dEntry>) : Fetch
        /** 400: no linked Minecraft account. */
        object NotLinked : Fetch
        data class Error(val message: String?) : Fetch
    }

    sealed interface Write {
        object Ok : Write
        /** 403: not unlocked, or disabled. */
        object Locked : Write
        /** 409: tail and cape together. Has the server's text. */
        data class TailCapeConflict(val message: String?) : Write
        /** 400: equipped limit, or a refused tint/modifier. */
        data class Invalid(val message: String?) : Write
        /** 404: not in the catalog, or not worn. */
        object NotFound : Write
        data class Error(val message: String?) : Write
    }

    fun fetch(): Fetch {
        val response = Http.get(Backend.ME_COSMETICS3D_URL, authenticated = true)
            ?: return Fetch.Error("not authenticated")

        return when (response.status) {
            200 -> runCatching<Fetch> {
                Fetch.Loaded(Cosmetic3dEntry.parseList(JsonParser.parseString(response.body).asJsonArray))
            }.getOrElse { Fetch.Error(it.toString()) }
            400 -> Fetch.NotLinked
            else -> Fetch.Error("HTTP ${response.status}")
        }
    }

    fun equip(
        id: String,
        tints: Map<String, String>,
        modifiers: Map<String, String>,
        adjust: Map<String, Double>,
    ): Write {
        val body = JsonObject().apply {
            add("tints", jsonOf(tints))
            add("modifiers", jsonOf(modifiers))
            add(CosmeticAdjust.KEY, numbersOf(adjust))
        }
        return write(Http.Method.PUT, id, body.toString())
    }

    fun unequip(id: String): Write = write(Http.Method.DELETE, id, body = null)

    private fun write(method: Http.Method, id: String, body: String?): Write {
        val response = Http.send(
            "${Backend.ME_COSMETICS3D_URL}/$id",
            method = method,
            body = body,
            contentType = body?.let { "application/json" },
            authenticated = true,
        ) ?: return Write.Error("not authenticated")

        return when (response.status) {
            in 200..299 -> Write.Ok
            403 -> Write.Locked
            404 -> Write.NotFound
            409 -> Write.TailCapeConflict(response.error)
            400 -> Write.Invalid(response.error)
            else -> Write.Error("HTTP ${response.status}")
        }
    }

    private fun jsonOf(values: Map<String, String>): JsonObject =
        JsonObject().apply { values.forEach { (k, v) -> addProperty(k, v) } }

    private fun numbersOf(values: Map<String, Double>): JsonObject =
        JsonObject().apply { values.forEach { (k, v) -> addProperty(k, v) } }
}
