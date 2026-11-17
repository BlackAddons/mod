package org.blackaddons.api.event.render

import org.blackaddons.api.event.Event

/** After the lightmap state is extracted. Set [fullbright] for a full-bright lightmap this frame. */
class LightmapRenderEvent(
    var fullbright: Boolean = false,
) : Event()
