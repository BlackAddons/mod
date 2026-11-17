package org.blackaddons.core.profile

import java.util.UUID

// The public model is in org.blackaddons.api.skyblock.profile. These classes stay on the host side.

/** The host's key format: lowercase, without dashes. */
internal val UUID.dashless: String get() = toString().replace("-", "")

internal data class SkinTextures(val value: String, val signature: String?)

/** [TeammateHistory] stores this through Gson, so renaming a field breaks the saved history. */
internal data class RecentActivity(
    val id: String,
    val completedAt: Long,
    /** `F7`, `M7`, `Entrance`, or a Kuudra tier. */
    val activity: String,
    val kuudra: Boolean,
    val participants: List<RecentParticipant>,
)

internal data class RecentParticipant(
    val uuid: String,
    /** Null when Hypixel's text can't be read safely. */
    val name: String?,
    val role: String?,
)
