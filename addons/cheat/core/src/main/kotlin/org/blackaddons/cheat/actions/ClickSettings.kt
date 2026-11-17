package org.blackaddons.cheat.actions

import org.blackaddons.api.Platform
import org.blackaddons.api.feature.FeatureExtension
import org.blackaddons.core.features.impl.settings.Actions

/**
 * Anticheat settings for synthetic clicks. The interaction host applies them, so every clicking feature gets them for
 * free.
 */
internal object ClickSettings : FeatureExtension(Actions, group = "Experimental") {

    // Pushed to the host, because the host can't read settings. Both start as false.
    private val settleBeforeClicks by boolean("Settle Aim Before Clicks", default = false)
        .onChange { Platform.interaction.settleBeforeClicks = it }
        .withDescription("Grim DuplicateRotPlace bypass. Before a click that follows a big turn, turn the aim a tiny random amount that stays on the target, and keep the aim still through the click. Not necessary: repeating the exact same turn is unlikely.")
}
