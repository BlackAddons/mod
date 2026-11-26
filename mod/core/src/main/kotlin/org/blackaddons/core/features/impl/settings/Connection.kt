package org.blackaddons.core.features.impl.settings

import org.blackaddons.api.feature.Feature
import org.blackaddons.api.feature.annotations.AlwaysActive
import org.blackaddons.core.backend.Network

/**
 * Push vs polling, not on vs off. Without the socket the mod polls, so everything still updates (late) and nothing
 * about the player gets reported.
 */
@AlwaysActive
object Connection : Feature(
    description = "How the mod talks to the backend.",
    name = "Connection",
) {
    private val liveUpdatesSetting by boolean(
        name = "Live Updates",
        default = true,
    ).withDescription(
        "Keep a connection open so cosmetics and notices arrive the moment they change, and count " +
                "you among the players online. Off, the mod refreshes on a timer instead and reports nothing.",
    )

    private val onlineMarkersSetting by boolean(
        name = "Online Markers",
        default = true,
    ).withDescription(
        "Show a dot beside players who are online in BlackAddons right now. Nobody is marked as " +
                "absent — a player with live updates turned off simply isn't connected.",
    )

    @Suppress("unused")
    private val restartSocket by button("Restart Live Updates", action = { Network.restartSocket() }, label = "Restart")
        .withDescription("Drop the live-updates connection and open it again right away. The same as /blackaddons net socket.")

    @Suppress("unused")
    private val restartNetwork by button("Restart Networking", action = { Network.restartAll() }, label = "Restart")
        .withDescription(
            "Start everything the mod uses to reach the server over: connections, sign-in and the live-updates " +
                    "connection. For when nothing reconnects on its own. The same as /blackaddons net restart.",
        )

    val liveUpdates: Boolean get() = liveUpdatesSetting.value

    /** Needs [liveUpdates] in practice: without a socket there's nothing to show. */
    val onlineMarkers: Boolean get() = onlineMarkersSetting.value
}
