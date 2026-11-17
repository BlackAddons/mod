package org.blackaddons.core.feature

import org.blackaddons.api.feature.Feature
import org.blackaddons.api.feature.FeatureExtension

internal class FeatureRegistrationValidator {

    private val featureKeys = mutableSetOf<FeatureKey>()
    private val hudKeys = mutableSetOf<HudKey>()

    /** After it contributed: its settings are in the host, so collisions there break the config too. */
    fun rejectionFor(extension: FeatureExtension): String? {
        if (extension.id.isBlank()) return "Extension ID is blank"
        val duplicate = extension.host.configSettings
            .groupingBy { it.id }
            .eachCount()
            .entries
            .firstOrNull { (id, count) -> id.isBlank() || count > 1 }
            ?: return null
        return if (duplicate.key.isBlank()) "A setting ID is blank" else "Duplicate setting ID '${duplicate.key}'"
    }

    fun rejectionFor(feature: Feature, addonId: String?): String? {
        if (feature.id.isBlank()) return "Feature ID is blank"

        val featureKey = FeatureKey(addonId, feature.id)
        if (featureKey in featureKeys) return "Duplicate feature ID '${feature.id}'"

        val duplicateSetting = feature.configSettings
            .groupingBy { it.id }
            .eachCount()
            .entries
            .firstOrNull { (id, count) -> id.isBlank() || count > 1 }
        if (duplicateSetting != null) {
            return if (duplicateSetting.key.isBlank()) {
                "A setting ID is blank"
            } else {
                "Duplicate setting ID '${duplicateSetting.key}'"
            }
        }

        val featureHudKeys = feature.hudElements.map { HudKey(addonId, it.name) }
        if (featureHudKeys.any { it.name.isBlank() }) return "A HUD name is blank"
        val duplicateHud = featureHudKeys
            .groupingBy { it }
            .eachCount()
            .entries
            .firstOrNull { (key, count) -> count > 1 || key in hudKeys }
            ?.key
        if (duplicateHud != null) return "Duplicate HUD name '${duplicateHud.name}'"

        featureKeys += featureKey
        hudKeys += featureHudKeys
        return null
    }

    private data class FeatureKey(val addonId: String?, val id: String)
    private data class HudKey(val addonId: String?, val name: String)
}
