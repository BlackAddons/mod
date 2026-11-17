package org.blackaddons.api.host

import com.google.gson.JsonElement

/**
 * Per-addon key-value JSON store at `config/blackaddons/addons/<addonId>.json`, for state that isn't a
 * setting. Loaded lazily; saves are atomic (temp file + rename).
 */
interface AddonStorage {
    fun has(key: String): Boolean
    fun getString(key: String, default: String? = null): String?
    fun getInt(key: String, default: Int = 0): Int
    fun getLong(key: String, default: Long = 0L): Long
    fun getDouble(key: String, default: Double = 0.0): Double
    fun getBoolean(key: String, default: Boolean = false): Boolean
    fun getJson(key: String): JsonElement?

    fun putString(key: String, value: String)
    fun putInt(key: String, value: Int)
    fun putLong(key: String, value: Long)
    fun putDouble(key: String, value: Double)
    fun putBoolean(key: String, value: Boolean)
    fun putJson(key: String, value: JsonElement)

    fun remove(key: String)
    fun clear()
    fun keys(): Set<String>

    /** Puts already auto-save; this forces a flush. */
    fun save()
}

interface AddonStorages {
    fun forAddon(addonId: String): AddonStorage
}
