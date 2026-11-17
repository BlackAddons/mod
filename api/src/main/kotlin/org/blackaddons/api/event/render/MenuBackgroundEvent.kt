package org.blackaddons.api.event.render

import org.blackaddons.api.event.Event

/**
 * Posted where vanilla draws the menu panorama: the title screen and every menu without a world. Draw a background
 * over the whole window with `Render` and set [replaced] to skip the panorama. The screen's own widgets still draw
 * on top.
 */
class MenuBackgroundEvent(var replaced: Boolean = false) : Event()
