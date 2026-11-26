package org.blackaddons.core.cosmetics.store
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.blackaddons.api.util.io.parseJsonObject
import org.blackaddons.core.backend.Http
import org.blackaddons.core.log.Log
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

internal class HttpCosmeticsSource(private val url: String) : CosmeticsSource {

    override fun resolve(uuids: List<UUID>): CosmeticsAnswer {
        if (uuids.isEmpty()) return CosmeticsAnswer.Failed
        val body = JsonObject().apply {
            add("players", JsonArray().apply { uuids.forEach { add(it.toString()) } })
        }
        val response = Http.send(url, Http.Method.POST, body = body.toString(), contentType = "application/json")
            ?: return CosmeticsAnswer.Failed
        // Wait exactly as long as told. A sooner retry just gets another refusal.
        if (response.busy) return CosmeticsAnswer.Later(response.retryAfterSeconds)
        if (response.status != 200) return CosmeticsAnswer.Failed
        val manifest = parseJsonObject(response.body)
            ?: return CosmeticsAnswer.Failed
        return if (supportedSchema(manifest)) CosmeticsAnswer.Manifest(manifest) else CosmeticsAnswer.Failed
    }

    /** An unknown major version would render players wrong, so keep current data. No field means pre-versioning. */
    private fun supportedSchema(manifest: JsonObject): Boolean {
        val version = manifest.get("version")?.takeIf { it.isJsonPrimitive }?.asInt ?: return true
        if (version == SCHEMA_VERSION) return true
        if (warnedSchema.compareAndSet(false, true)) {
            Log.main.warn("Cosmetics manifest is schema v{}, this build reads v{} - update the mod", version, SCHEMA_VERSION)
        }
        return false
    }

    private companion object {
        const val SCHEMA_VERSION = 1

        val warnedSchema = AtomicBoolean(false)
    }
}
