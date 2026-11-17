package org.blackaddons.api.event.world

import org.blackaddons.api.event.Cancellable
import org.blackaddons.api.event.Event
import java.nio.file.Path

/** A screenshot was written to [path]. Client thread. Cancel to suppress the vanilla "Saved screenshot as ..." line. */
class ScreenshotEvent(val path: Path) : Event(), Cancellable {
    override var cancelled: Boolean = false
}
