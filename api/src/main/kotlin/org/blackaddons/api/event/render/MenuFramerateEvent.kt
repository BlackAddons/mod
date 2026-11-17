package org.blackaddons.api.event.render

import org.blackaddons.api.event.Event

/**
 * Posted each frame while vanilla caps a menu without a world to 60 FPS. Set [limit] to lift the cap or to match the
 * monitor.
 */
class MenuFramerateEvent(var limit: Limit = Limit.VANILLA) : Event() {

    enum class Limit {
        VANILLA,
        UNLIMITED,
        DISPLAY, // Basically vsync.
    }
}
