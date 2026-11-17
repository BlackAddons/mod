package org.blackaddons.core.features.impl.settings

import org.blackaddons.api.feature.Categories
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.feature.annotations.AlwaysActive
import org.blackaddons.api.ui.toast.Toasts

@AlwaysActive
object Notifications : Feature(
    description = "Short notices in a corner of the screen - copied, saved, the servers are down.",
    name = "Notifications",
) {

    override val section = Categories.Settings.Appearance

    private val duration by number(
        "Duration",
        default = 1.0,
        min = 0.5,
        max = 3.0,
        step = 0.25,
        unit = "x",
    ).withDescription("How long a notice stays on screen. Longer notices already stay longer on their own.")

    override fun init() {
        hudElements += Toasts.element
        Toasts.durationScale = { duration.value.toFloat() }
    }
}
