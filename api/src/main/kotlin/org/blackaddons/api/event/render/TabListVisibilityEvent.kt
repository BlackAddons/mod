package org.blackaddons.api.event.render

import org.blackaddons.api.event.Event

/** [visible] starts as the vanilla key-held state; listeners may override it. */
class TabListVisibilityEvent(
    var visible: Boolean,
) : Event()
