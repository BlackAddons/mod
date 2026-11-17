package org.blackaddons.core.features.impl.client

import org.blackaddons.api.Platform
import org.blackaddons.api.feature.Categories
import org.blackaddons.api.feature.Feature
import org.blackaddons.core.features.impl.client.WindowIcon.LARGE
import org.blackaddons.core.features.impl.client.WindowIcon.SMALL

/** The OS picks a size for each place: [SMALL] for the taskbar and the title bar, [LARGE] for Alt+Tab and big icons. */
object WindowIcon : Feature(
    description = "Shows the BlackAddons logo as the window and taskbar icon.",
) {
    override val section = Categories.Client.Menu

    private val SMALL = listOf(16, 32, 48)
    private val LARGE = listOf(128, 256, 512, 1024)
    private const val ROOT = "/assets/blackaddons/window_icon"

    private val style by mode("Small Icon", default = "Legacy", modes = listOf("Legacy", "Alt"))
        .withDescription("The taskbar and title bar logo. The full logo is too detailed to read at that size.")
        .onChange { if (enabled) apply() }

    private val large by lazy { load("large", LARGE) }
    private val small = mutableMapOf<String, Map<Int, ByteArray>>()

    override fun onEnable() = apply()

    override fun onDisable() = Platform.desktop.setWindowIcon(null)

    private fun apply() {
        val folder = style.value.lowercase()
        Platform.desktop.setWindowIcon(small.getOrPut(folder) { load(folder, SMALL) } + large)
    }

    private fun load(folder: String, sizes: List<Int>): Map<Int, ByteArray> = sizes.associateWith { size ->
        val path = "$ROOT/$folder/$size.png"
        checkNotNull(WindowIcon::class.java.getResourceAsStream(path)) { "Missing $path" }.use { it.readBytes() }
    }
}
