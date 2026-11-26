package org.blackaddons.core.cosmetics.store

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.blackaddons.api.cosmetics.*
import org.blackaddons.api.util.Uuids
import org.blackaddons.core.cosmetics.GradientStops
import org.blackaddons.core.cosmetics.NickStyles
import org.blackaddons.core.cosmetics.TintHex
import org.blackaddons.core.cosmetics.mesh.CosmeticModifiers
import org.blackaddons.core.cosmetics.mesh.withoutSideMirror
import org.blackaddons.core.log.Log
import java.util.UUID

/**
 * Backend cosmetics <-> [Cosmetic]. Two shapes over one nick record per player: the resolve response
 * `{ "version": 1, "players": { "<uuid>": { "nick": {...}, "items": [...] } } }` and the flat disk cache
 * `{ "<uuid>": {...nick...} }`. Nick record:
 * ```json
 * { "displayName": "BLACKUM", "prefix": "[VIP]",
 *   "color":       { "type": "Gradient", "gradient": { "stops": [...] }, "perCharacter": false, "animated": true, "speed": 1.0 },
 *   "prefixColor": { "type": "Solid", "rgb": 16711680 },
 *   "scale": { "x": 1.0, "y": 1.0, "z": 1.0 },
 *   "capeUrl": "https://.../v1/capes/<uuid>?v=<hash>",
 *   "style": { "bold": true }, "prefixStyle": { "italic": true },
 *   "font": { "id": "comfortaa", "family": "Comfortaa", "weight": 400, "italic": false,
 *             "files": [ { "subset": "latin", "url": "https://...@5.3.0/latin-400-normal.ttf", "range": "U+0000-00FF" } ] } }
 * ```
 * `perCharacter` is the inverse of [ColorSpec.wholeTextGradient]. `items` become [Cosmetic3dItem]s (not cached).
 * Unknown keys are ignored.
 */
internal object CosmeticJson {
    /** Other schema versions get skipped, not guessed. */
    private const val SCHEMA_VERSION = 1

    fun fromResolve(root: JsonObject): Map<UUID, Cosmetic> {
        val version = int(root, "version")
        if (version != null && version != SCHEMA_VERSION) {
            Log.main.debug("Ignoring cosmetics manifest of unknown schema version {}", version)
            return emptyMap()
        }
        val players = root.get("players") as? JsonObject ?: return emptyMap()
        val out = HashMap<UUID, Cosmetic>()
        for ((key, value) in players.entrySet()) {
            val entry = value as? JsonObject ?: continue
            val uuid = Uuids.parse(key) ?: continue
            val nick = entry.get("nick") as? JsonObject
            val items = parseItems(entry.get("items") as? JsonArray)
            if (nick == null && items.isEmpty()) continue
            val base = if (nick != null) parseNick(nick) else Cosmetic()
            out[uuid] = if (items.isEmpty()) base else base.copy(items = items)
        }
        return out
    }

    fun fromCache(root: JsonObject): Map<UUID, Cosmetic> {
        val out = HashMap<UUID, Cosmetic>()
        for ((key, value) in root.entrySet()) {
            val o = value as? JsonObject ?: continue
            val uuid = Uuids.parse(key) ?: continue
            out[uuid] = parseNick(o)
        }
        return out
    }

    fun toCache(map: Map<UUID, Cosmetic>): JsonObject = JsonObject().apply {
        for ((uuid, cosmetic) in map) add(uuid.toString(), writeNick(cosmetic))
    }

    private fun parseNick(o: JsonObject): Cosmetic = Cosmetic(
        display = string(o, "displayName"),
        name = parseColor(o.get("color") as? JsonObject),
        prefix = string(o, "prefix"),
        prefixColor = parseColor(o.get("prefixColor") as? JsonObject),
        size = (o.get("scale") as? JsonObject)?.let {
            Scale(float(it, "x") ?: 1f, float(it, "y") ?: 1f, float(it, "z") ?: 1f).withoutSideMirror()
        },
        capeUrl = string(o, "capeUrl"),
        style = NickStyles.parse(o.get("style") as? JsonObject),
        prefixStyle = NickStyles.parse(o.get("prefixStyle") as? JsonObject),
        font = parseFont(o.get("font") as? JsonObject),
    )

    // A font without a usable file counts as no font, and the name keeps the game's.
    internal fun parseFont(o: JsonObject?): NickFont? {
        if (o == null) return null
        val files = (o.get("files") as? JsonArray)?.mapNotNull { element ->
            val f = element as? JsonObject ?: return@mapNotNull null
            NickFontFile(string(f, "subset") ?: return@mapNotNull null, string(f, "url") ?: return@mapNotNull null,
                string(f, "range") ?: return@mapNotNull null)
        }
        if (files.isNullOrEmpty()) return null
        return NickFont(string(o, "id") ?: return null, string(o, "family") ?: return null,
            int(o, "weight") ?: 400, bool(o, "italic"), files)
    }

    private fun writeFont(font: NickFont): JsonObject = JsonObject().apply {
        addProperty("id", font.id)
        addProperty("family", font.family)
        addProperty("weight", font.weight)
        addProperty("italic", font.italic)
        add("files", JsonArray().apply {
            font.files.forEach { f ->
                add(JsonObject().apply { addProperty("subset", f.subset); addProperty("url", f.url); addProperty("range", f.range) })
            }
        })
    }

    private fun parseColor(color: JsonObject?): ColorSpec = when (color?.let { string(it, "type") }) {
        "Solid" -> ColorSpec(solidColor = int(color, "rgb")?.and(0xFFFFFF))
        "Gradient" -> ColorSpec(
            gradient = GradientStops.parse(color.get("gradient") as? JsonObject),
            animated = bool(color, "animated"),
            speed = float(color, "speed") ?: 1f,
            wholeTextGradient = !bool(color, "perCharacter"),
        )
        "Chroma" -> ColorSpec(
            chroma = true,
            animated = bool(color, "animated"),
            speed = float(color, "speed") ?: 1f,
        )
        else -> ColorSpec.VANILLA
    }

    private fun writeNick(c: Cosmetic): JsonObject = JsonObject().apply {
        c.display?.let { addProperty("displayName", it) }
        add("color", writeColor(c.name))
        c.prefix?.let { addProperty("prefix", it) }
        if (c.hasPrefix) add("prefixColor", writeColor(c.prefixColor))
        c.size?.let { s ->
            add("scale", JsonObject().apply {
                addProperty("x", s.x); addProperty("y", s.y); addProperty("z", s.z)
            })
        }
        c.capeUrl?.let { addProperty("capeUrl", it) }
        NickStyles.toJson(c.style)?.let { add("style", it) }
        if (c.hasPrefix) NickStyles.toJson(c.prefixStyle)?.let { add("prefixStyle", it) }
        c.font?.let { add("font", writeFont(it)) }
    }

    private fun writeColor(spec: ColorSpec): JsonObject = JsonObject().apply {
        val solid = spec.solidColor // captured: a cross-module nullable property won't smart-cast
        when {
            spec.chroma -> {
                addProperty("type", "Chroma")
                addProperty("animated", spec.animated)
                addProperty("speed", spec.speed)
            }
            spec.gradient.isNotEmpty() -> {
                addProperty("type", "Gradient")
                add("gradient", GradientStops.toJson(spec.gradient))
                addProperty("perCharacter", !spec.wholeTextGradient)
                addProperty("animated", spec.animated)
                addProperty("speed", spec.speed)
            }
            solid != null -> {
                addProperty("type", "Solid")
                addProperty("rgb", solid and 0xFFFFFF)
            }
            else -> addProperty("type", "Vanilla")
        }
    }

    private fun parseItems(arr: JsonArray?): List<Cosmetic3dItem> {
        if (arr == null) return emptyList()
        val out = ArrayList<Cosmetic3dItem>(arr.size())
        for (el in arr) (el as? JsonObject)?.let { parseItem(it)?.let(out::add) }
        return out
    }

    private fun parseItem(o: JsonObject): Cosmetic3dItem? =
        itemFromDefinition(o, string(o, "id") ?: return null)

    /** From a fragment with asset `url`s. Local resolves take [id] from the catalog entry. See [CosmeticModifiers]. */
    fun itemFromDefinition(o: JsonObject, id: String): Cosmetic3dItem? {
        val model = parseAsset(o.get("model") as? JsonObject) ?: return null
        return Cosmetic3dItem(
            id = id,
            attach = parseAttach(o.get("attach") as? JsonObject),
            model = model,
            clip = string(o, "clip"),
            layers = parseLayers(o.get("layers") as? JsonArray),
            tints = parseTints(o.get("tints") as? JsonObject),
        )
    }

    private fun parseAttach(o: JsonObject?): CosmeticAttach {
        val offset = o?.get("offset") as? JsonArray
        val rotation = o?.get("rotation") as? JsonArray
        return CosmeticAttach(
            point = o?.let { string(it, "point") } ?: "",
            offsetX = floatAt(offset, 0), offsetY = floatAt(offset, 1), offsetZ = floatAt(offset, 2),
            pitch = floatAt(rotation, 0), yaw = floatAt(rotation, 1), roll = floatAt(rotation, 2),
            scale = o?.let { float(it, "scale") } ?: 1f,
        )
    }

    private fun parseAsset(o: JsonObject?): AssetRef? {
        o ?: return null
        val sha = string(o, "sha256") ?: return null
        val url = string(o, "url") ?: return null
        return AssetRef(sha, url, string(o, "format"))
    }

    private fun parseLayers(arr: JsonArray?): List<CosmeticLayer> {
        if (arr == null) return emptyList()
        val out = ArrayList<CosmeticLayer>(arr.size())
        for (el in arr) {
            val o = el as? JsonObject ?: continue
            val asset = parseAsset(o) ?: continue
            out.add(
                CosmeticLayer(
                    role = string(o, "role") ?: "base",
                    asset = asset,
                    tintSlot = string(o, "tintSlot"),
                    supportsAlpha = bool(o, "supportsAlpha"),
                )
            )
        }
        return out
    }

    private fun parseTints(o: JsonObject?): Map<String, Int> {
        if (o == null) return emptyMap()
        val out = HashMap<String, Int>()
        for ((slot, v) in o.entrySet()) {
            val hex = v?.takeIf { !it.isJsonNull }?.asString ?: continue
            TintHex.parse(hex)?.let { out[slot] = it }
        }
        return out
    }

    private fun floatAt(arr: JsonArray?, i: Int): Float =
        arr?.takeIf { i < it.size() }?.get(i)?.takeIf { it.isJsonPrimitive }?.asFloat ?: 0f

    private fun string(o: JsonObject, key: String): String? =
        o.get(key)?.takeIf { !it.isJsonNull }?.asString

    private fun bool(o: JsonObject, key: String): Boolean =
        o.get(key)?.takeIf { !it.isJsonNull }?.asBoolean ?: false

    private fun float(o: JsonObject, key: String): Float? =
        o.get(key)?.takeIf { !it.isJsonNull }?.asFloat

    private fun int(o: JsonObject, key: String): Int? =
        o.get(key)?.takeIf { !it.isJsonNull }?.asInt
}
