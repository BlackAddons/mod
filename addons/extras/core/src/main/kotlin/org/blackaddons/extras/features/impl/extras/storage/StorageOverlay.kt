package org.blackaddons.extras.features.impl.extras.storage

import org.blackaddons.api.Platform
import org.blackaddons.api.commands.ArgType.IntArg
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.feature.Feature

/**
 * Offline storage browser drawn over the live screen; clicking a page relays `/enderchest` or `/backpack`. The open
 * page's real slots sit under the grid, so item moves are native. Reimplemented from NotEnoughUpdates' `StorageOverlay`
 * behavior (LGPL).
 */
object StorageOverlay : Feature(
    description = "Browse and search your ender chest and storage backpacks offline.",
    name = "Storage Overlay",
) {
    val replaceStorageMenu by boolean("Storage Browser", default = true)
        .withDescription("Draw the offline browser over the in-game Storage menu and pages.")

    val commandAlias by boolean("/ec, /enderchest -> Storage", default = true)
        .withDescription("On Skyblock, /ec and /enderchest open the storage browser (with backpacks).")

    val pagesPerRow by number("Pages Per Row", default = 3.0, min = 1.0, max = 8.0)
        .withDescription("Most storage pages side by side; narrow screens fit fewer.")

    @Suppress("unused")
    private val clearCache by button(
        name = "Clear Cache",
        action = { StorageCache.clearProfile() },
        label = "Clear",
    )

    private var wasOpen = false

    override fun init() {
        StoragePanel.active = { enabled && replaceStorageMenu.value }
        StoragePanel.pagesPerRow = { pagesPerRow.value.toInt() }
        Platform.containerOverlays.register(StoragePanel)
        register<ClientTickEvent.Post> {
            StorageCapturer.tick()
            StoragePanel.invalidateLivePage()
            // Page switches keep search and scroll. Closing drops them and releases text focus.
            val open = Platform.container.title()?.let(StoragePanel::appliesTo) == true
            if (wasOpen && !open) StoragePanel.reset()
            wasOpen = open
        }
        register<WorldChangeEvent> {
            StoragePanel.reset()
            wasOpen = false
        }
        registerStorageAliases()
    }

    override fun onDisable() {
        super.onDisable()
        StoragePanel.reset()
        wasOpen = false
    }

    /**
     * Makes /ec and /enderchest open the browser on Skyblock (bare /enderchest skips backpacks), a page number opens
     * that page. Our own relay bypasses the client dispatcher.
     */
    private fun registerStorageAliases() {
        for (alias in listOf("ec", "enderchest")) {
            Platform.commands.register(alias) {
                execute {
                    if (enabled && commandAlias.value && Platform.skyblock.onSkyblock) {
                        Platform.chat.sendCommand("storage")
                    } else {
                        Platform.chat.sendCommand(alias)
                    }
                }
                arg("page", IntArg(min = 1)) {
                    execute { Platform.chat.sendCommand("enderchest ${int("page")}") }
                }
            }
        }
    }
}
