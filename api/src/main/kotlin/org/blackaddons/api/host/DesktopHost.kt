package org.blackaddons.api.host

import java.nio.file.Path

/** Opens links and folders through Minecraft's per-OS handling. */
interface DesktopHost {

    /** Only `http`/`https`; anything else is refused. */
    fun openUrl(url: String)

    /** Creates the directory first if missing. */
    fun openPath(path: Path)

    /** OS file picker. Validate the file by content; [onSelected] runs on the client thread, cancel does nothing. */
    fun chooseFile(title: String, extensions: Set<String>, onSelected: (Path) -> Unit)

    /** Replaces the window title and keeps it when the game refreshes the title. Null restores vanilla's. */
    fun setWindowTitle(title: String?)

    /**
     * Replaces the window and taskbar icon. Null restores vanilla's. Takes square PNGs by edge size in pixels:
     * 16, 32, 48, 128 and 256 are the window icon, and 128 to 1024 are the macOS dock icon where the game draws
     * one.
     */
    fun setWindowIcon(pngBySize: Map<Int, ByteArray>?)
}
