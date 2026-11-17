package org.blackaddons.api.host

import java.nio.file.Path

interface ClipboardHost {
    /** No-op if the clipboard is unavailable. */
    fun copy(text: String)
    fun paste(): String

    /** Copies the image at [path] through the OS (the game clipboard is text-only). Async; [onDone] reports success. */
    fun copyImage(path: Path, onDone: (Boolean) -> Unit = {}) = onDone(false)
}
