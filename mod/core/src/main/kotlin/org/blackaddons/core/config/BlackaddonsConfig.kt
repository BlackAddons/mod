package org.blackaddons.core.config

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import org.blackaddons.api.Platform
import org.blackaddons.api.config.ConfigHost
import org.blackaddons.api.config.Savable
import org.blackaddons.api.hud.AnchorX
import org.blackaddons.api.hud.AnchorY
import org.blackaddons.api.input.InputBinding
import org.blackaddons.api.util.io.Gsons
import org.blackaddons.core.config.BlackaddonsConfig.save
import org.blackaddons.core.config.portable.FeatureSettingsKind
import org.blackaddons.core.feature.FeatureManager
import org.blackaddons.core.log.Log
import org.blackaddons.core.storage.GuardedFile

/**
 * JSON persistence for features and HUD elements:
 * ```
 * {
 *   "features": [ { "id": "FpsDisplay", "addon": "blackaddons-cheat", "enabled": true, "settings": { ... } } ],
 *   "huds": [ { "name": "FpsDisplay HUD", "anchorX": "RIGHT", "anchorY": "BOTTOM",
 *               "offsetX": -5.0, "offsetY": -5.0, "scaleX": 1.5, "scaleY": 1.5 } ]
 * }
 * ```
 * `"addon"` tells apart entries with the same name (omitted for first-party). Entries for unavailable features,
 * HUDs or addon-injected settings are kept and written back, so switching editions loses nothing. An
 * unreadable config is never overwritten ([GuardedFile]). Legacy `"x"`/`"y"` become a LEFT/TOP offset.
 */
object BlackaddonsConfig : ConfigHost {

    private val path get() = Platform.configDir.resolve("blackaddons.json")

    /**
     * One monitor for read, import, share and save. save() runs on any thread. Fragments of missing addons are kept,
     * that's normal.
     */
    private val unavailableFeatures = mutableListOf<JsonElement>()
    private val unavailableHuds = mutableListOf<JsonElement>()
    private val unavailableSettings = mutableMapOf<Pair<String, String?>, JsonObject>()

    // Holds the failed-read latch. Lazy, because the config dir isn't known during init.
    private var file: GuardedFile? = null

    @Volatile
    private var hasRead = false

    private fun guard(): GuardedFile =
        file?.takeIf { it.path == path } ?: GuardedFile(path).also { file = it }

    /** Reads, then activates every feature, also with no file (fresh install). */
    fun load() {
        read()
        FeatureManager.activate()
    }

    /** Doesn't activate. Startup runs the addons' `onLoad` in between. */
    @Synchronized
    fun read() {
        hasRead = true
        unavailableFeatures.clear()
        unavailableHuds.clear()
        unavailableSettings.clear()
        file = GuardedFile(path)
        val text = guard().read() ?: return
        runCatching {
            apply(JsonParser.parseString(text).asJsonObject ?: return, shared = false)
            Log.main.debug("Loaded config from {}", path)
        }.onFailure {
            Log.main.error("Failed to load config {}", path, it)
            // Partially applied. Writing it back would save half a config.
            guard().refuseWrites("it could not be parsed")
        }
    }

    /** Applies a shared config on top and saves. Entries it doesn't mention and masked settings stay. */
    @Synchronized
    fun importShared(document: JsonObject) {
        apply(document, shared = true)
        FeatureManager.activate()
        save()
    }

    /** What [save] writes, without unloaded addons and masked settings. */
    @Synchronized
    fun shareable(): JsonObject = document(shared = true)

    /** Shared documents drop unknown features. The player's file keeps them. */
    private fun apply(root: JsonObject, shared: Boolean) {
        // Activation waits until everything is read, so no onEnable sees another feature's defaults.
        root.getAsJsonArray("features")?.forEach { featureEl ->
            val obj = featureEl.asJsonObject ?: return@forEach unavailable(featureEl, unavailableFeatures, shared)
            val id = obj.get("id")?.asString ?: return@forEach unavailable(featureEl, unavailableFeatures, shared)
            val addonId = obj.get("addon")?.asString  // null on legacy entries
            val feature = FeatureManager.featureById(id, addonId) ?: return@forEach unavailable(featureEl, unavailableFeatures, shared)

            if (!feature.isAlwaysActive) {
                obj.get("enabled")?.takeIf { it.isJsonPrimitive }?.asBoolean?.let { feature.enabled = it }
            }
            if (!shared) feature.toggleBind.binding = InputBinding.fromJson(obj.get("toggleKey"))

            obj.get("settings")?.takeIf { it.isJsonObject }?.asJsonObject?.let { settings ->
                if (shared) {
                    FeatureSettingsKind.staged(feature, settings).forEach { (setting, value) -> (setting as Savable).read(value) }
                    return@let
                }
                val preserved = JsonObject()
                settings.entrySet().forEach { (sid, el) ->
                    val setting = feature.getSettingById(sid)
                    if (setting is Savable) setting.read(el) else preserved.add(sid, el.deepCopy())
                }
                if (!preserved.isEmpty) {
                    unavailableSettings[feature.id to FeatureManager.addonIdOf(feature)] = preserved
                }
            }
        }

        root.getAsJsonArray("huds")?.forEach { hudEl ->
            val obj = hudEl.asJsonObject ?: return@forEach unavailable(hudEl, unavailableHuds, shared)
            val name = obj.get("name")?.asString ?: return@forEach unavailable(hudEl, unavailableHuds, shared)
            val addonId = obj.get("addon")?.asString
            val hud = FeatureManager.hudByName(name, addonId) ?: return@forEach unavailable(hudEl, unavailableHuds, shared)
            obj.get("scaleX")?.takeIf { it.isJsonPrimitive }?.asFloat?.let { hud.scaleX = it }
            obj.get("scaleY")?.takeIf { it.isJsonPrimitive }?.asFloat?.let { hud.scaleY = it }
            obj.get("font")?.takeIf { it.isJsonPrimitive }?.asString?.let { hud.fontOverride = it }

            val hasAnchor = obj.has("anchorX") || obj.has("anchorY") || obj.has("offsetX") || obj.has("offsetY")
            if (hasAnchor) {
                obj.get("anchorX")?.takeIf { it.isJsonPrimitive }?.asString?.let { runCatching { hud.anchorX = AnchorX.valueOf(it) } }
                obj.get("anchorY")?.takeIf { it.isJsonPrimitive }?.asString?.let { runCatching { hud.anchorY = AnchorY.valueOf(it) } }
                obj.get("offsetX")?.takeIf { it.isJsonPrimitive }?.asFloat?.let { hud.offsetX = it }
                obj.get("offsetY")?.takeIf { it.isJsonPrimitive }?.asFloat?.let { hud.offsetY = it }
            } else {
                val lx = obj.get("x")?.takeIf { it.isJsonPrimitive }?.asFloat
                val ly = obj.get("y")?.takeIf { it.isJsonPrimitive }?.asFloat
                if (lx != null || ly != null) {
                    hud.anchorX = AnchorX.LEFT
                    hud.anchorY = AnchorY.TOP
                    hud.offsetX = lx ?: 0f
                    hud.offsetY = ly ?: 0f
                }
            }
        }
    }

    @Synchronized
    override fun save() {
        // Saving before the first read would overwrite the file with defaults.
        if (!hasRead) {
            Log.main.warn("Ignoring a config save requested before the config was read")
            return
        }
        runCatching {
            if (guard().write(Gsons.pretty.toJson(document(shared = false)))) Log.main.debug("Saved config to {}", path)
        }.onFailure { Log.main.error("Failed to save config {}", path, it) }
    }

    private fun document(shared: Boolean): JsonObject = JsonObject().apply {
        add("features", JsonArray().apply {
            if (!shared) unavailableFeatures.forEach { add(it.deepCopy()) }
            for (feature in FeatureManager.features) {
                add(JsonObject().apply {
                    add("id", JsonPrimitive(feature.id))
                    FeatureManager.addonIdOf(feature)?.let { addonId ->
                        add("addon", JsonPrimitive(addonId))
                    }
                    add("enabled", JsonPrimitive(feature.enabled))
                    if (!shared && feature.toggleBind.isBound) add("toggleKey", feature.toggleBind.write())
                    add("settings", JsonObject().apply {
                        if (!shared) {
                            unavailableSettings[feature.id to FeatureManager.addonIdOf(feature)]
                                ?.entrySet()
                                ?.forEach { (id, value) -> add(id, value.deepCopy()) }
                        }
                        for (setting in feature.configSettings) {
                            if (setting is Savable && (!shared || FeatureSettingsKind.transferable(setting))) {
                                add(setting.id, setting.write())
                            }
                        }
                    })
                })
            }
        })
        add("huds", JsonArray().apply {
            if (!shared) unavailableHuds.forEach { add(it.deepCopy()) }
            for (hud in FeatureManager.hudElements) {
                add(JsonObject().apply {
                    add("name", JsonPrimitive(hud.name))
                    FeatureManager.addonIdOf(hud)?.let { addonId ->
                        add("addon", JsonPrimitive(addonId))
                    }
                    add("anchorX", JsonPrimitive(hud.anchorX.name))
                    add("anchorY", JsonPrimitive(hud.anchorY.name))
                    add("offsetX", JsonPrimitive(hud.offsetX))
                    add("offsetY", JsonPrimitive(hud.offsetY))
                    add("scaleX", JsonPrimitive(hud.scaleX))
                    add("scaleY", JsonPrimitive(hud.scaleY))
                    add("font", JsonPrimitive(hud.fontOverride))
                })
            }
        })
    }

    /** TODO: delete on release, together with `GlobalFontStore.migrate`. */
    @Synchronized
    fun removedFeatureEnabled(id: String): Boolean? = unavailableFeatures.firstNotNullOfOrNull { element ->
        element.takeIf { it.isJsonObject }?.asJsonObject
            ?.takeIf { it.get("id")?.takeIf { id -> id.isJsonPrimitive }?.asString == id }
            ?.get("enabled")?.takeIf { it.isJsonPrimitive }?.asBoolean
    }

    private fun unavailable(element: JsonElement, into: MutableList<JsonElement>, shared: Boolean) {
        if (!shared) into += element.deepCopy()
    }
}
