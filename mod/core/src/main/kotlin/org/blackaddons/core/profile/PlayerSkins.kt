package org.blackaddons.core.profile

import org.blackaddons.api.util.concurrent.Workers
import org.blackaddons.core.log.Log
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Off-server head textures without Mojang proxy I/O on the render thread. */
internal object PlayerSkins {

    private val executor = Workers.pool("pv-skin", 2)
    private val cache = AsyncPlayerSkinCache(
        fetch = { uuid -> MojangResolver.texturesProperty(uuid)?.value },
        submit = { work -> executor.execute { work() } },
        onFailure = { uuid, error -> Log.main.warn("Skin lookup for {} failed", uuid, error) },
    )

    fun texture(uuid: String): String? = cache.texture(uuid)
}

/** Injectable core, to test the off-thread and dedup contract. */
internal class AsyncPlayerSkinCache(
    private val fetch: (String) -> String?,
    private val submit: (() -> Unit) -> Unit,
    private val onFailure: (String, Throwable) -> Unit = { _, _ -> },
) {
    private val textures = ConcurrentHashMap<String, String>()
    private val requested = ConcurrentHashMap.newKeySet<String>()

    fun texture(uuid: String): String? {
        val key = uuid.replace("-", "").lowercase(Locale.ROOT)
        if (key.isEmpty()) return null
        textures[key]?.let { return it }
        if (requested.add(key)) {
            submit {
                runCatching { fetch(key) }
                    .onSuccess { texture -> if (texture != null) textures[key] = texture }
                    .onFailure { onFailure(key, it) }
            }
        }
        return null
    }
}
