package org.blackaddons.api.event.skyblock

import org.blackaddons.api.event.Event

/**
 * You arrived in another SkyBlock area (`LocationUtils.area`), such as `Hub` or `The Catacombs`. The blank moment of
 * a server hop is skipped, so [current] is never null; [previous] is null for the first area after joining.
 */
class AreaChangeEvent(val previous: String?, val current: String) : Event()

/**
 * You are on another SkyBlock profile (`Platform.skyblock.profile`, its name such as `Apple`). Like
 * [AreaChangeEvent], [previous] is null for the first one after joining.
 */
class ProfileChangeEvent(val previous: String?, val current: String) : Event()
