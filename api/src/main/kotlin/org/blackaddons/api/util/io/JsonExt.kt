package org.blackaddons.api.util.io

import com.google.gson.*

/** Null-tolerant Gson navigation: missing or wrong-typed fields become null/defaults instead of throwing. */

/** [text] as a JSON object, or null when it doesn't parse or isn't an object. */
fun parseJsonObject(text: String): JsonObject? = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()

fun JsonObject?.child(key: String): JsonObject? =
    this?.get(key) as? JsonObject

fun JsonObject?.array(key: String): JsonArray? =
    this?.get(key) as? JsonArray

fun JsonObject?.str(key: String, default: String? = null): String? =
    (this?.get(key) as? JsonPrimitive)?.asString ?: default

fun JsonObject?.int(key: String, default: Int = 0): Int =
    (this?.get(key) as? JsonPrimitive)?.runCatching { asInt }?.getOrDefault(default) ?: default

fun JsonObject?.intOrNull(key: String): Int? =
    (this?.get(key) as? JsonPrimitive)?.runCatching { asInt }?.getOrNull()

fun JsonObject?.long(key: String, default: Long = 0L): Long =
    (this?.get(key) as? JsonPrimitive)?.runCatching { asLong }?.getOrDefault(default) ?: default

fun JsonObject?.longOrNull(key: String): Long? =
    (this?.get(key) as? JsonPrimitive)?.runCatching { asLong }?.getOrNull()

fun JsonObject?.float(key: String, default: Float = 0f): Float =
    (this?.get(key) as? JsonPrimitive)?.runCatching { asFloat }?.getOrDefault(default) ?: default

fun JsonObject?.floatOrNull(key: String): Float? =
    (this?.get(key) as? JsonPrimitive)?.runCatching { asFloat }?.getOrNull()

fun JsonObject?.double(key: String, default: Double = 0.0): Double =
    (this?.get(key) as? JsonPrimitive)?.runCatching { asDouble }?.getOrDefault(default) ?: default

fun JsonObject?.doubleOrNull(key: String): Double? =
    (this?.get(key) as? JsonPrimitive)?.runCatching { asDouble }?.getOrNull()

fun JsonObject?.bool(key: String, default: Boolean = false): Boolean =
    (this?.get(key) as? JsonPrimitive)?.runCatching { asBoolean }?.getOrDefault(default) ?: default

/** Drops explicit nulls recursively, so Gson leaves non-null Kotlin fields at their defaults. */
fun JsonElement.withoutNulls(): JsonElement = when {
    isJsonObject -> JsonObject().also { out ->
        asJsonObject.entrySet().forEach { (key, value) -> if (!value.isJsonNull) out.add(key, value.withoutNulls()) }
    }
    isJsonArray -> JsonArray().also { out ->
        asJsonArray.forEach { if (!it.isJsonNull) out.add(it.withoutNulls()) }
    }
    else -> this
}
