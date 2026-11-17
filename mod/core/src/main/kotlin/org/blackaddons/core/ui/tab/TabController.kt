package org.blackaddons.core.ui.tab

import org.blackaddons.api.ui.widget.InputTarget

/** One tab's content area. Coordinates are absolute scaled screen space. */
internal interface TabController : InputTarget {
    fun render(x: Float, y: Float, width: Float, height: Float, mouseX: Float, mouseY: Float)
}
