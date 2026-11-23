package org.blackaddons.core.features.impl.ui

import org.blackaddons.api.Platform
import org.blackaddons.api.event.world.ScreenshotEvent
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.ui.toast.ToastAction
import org.blackaddons.api.ui.toast.ToastImage
import org.blackaddons.api.ui.toast.ToastKind
import org.blackaddons.api.ui.toast.Toasts
import org.blackaddons.api.util.I18n
import org.blackaddons.core.screenshots.Screenshot
import org.blackaddons.core.screenshots.ScreenshotLibrary
import org.blackaddons.core.ui.screen.chat.ChatScreen
import org.blackaddons.core.ui.screen.screenshots.ScreenshotsScreen
import kotlin.io.path.getLastModifiedTime

/** Replaces the screenshot line with a notice: preview + send, open, copy, star and viewer buttons, clickable in chat. */
object Screenshots : Feature(
    description = "Shows a preview with quick actions when you take a screenshot, instead of a chat line.",
    name = "Screenshots",
    toggled = true,
) {
    private val tr = I18n.scope("blackaddons.screenshots")

    private val openKey by keybind(name = "Open Screenshots")
        .withDescription("Opens the screenshot viewer.")

    override fun init() {
        onKeybind(openKey) {
            ScreenshotsScreen.open()
        }

        register<ScreenshotEvent> {
            event.cancelled = true
            val shot = Screenshot(event.path, runCatching { event.path.getLastModifiedTime().toMillis() }.getOrDefault(0L))
            val ready = ScreenshotLibrary.thumbnail(shot) { preview -> Platform.scheduler.schedule(0) { announce(shot, preview) } }
            if (ready != null) announce(shot, ready)
        }
    }

    private fun announce(shot: Screenshot, preview: String) {
        Toasts.show(
            text = tr("saved", "Screenshot saved"),
            detail = shot.name,
            kind = ToastKind.SUCCESS,
            image = ToastImage(preview, Platform.window.scaledWidth / Platform.window.scaledHeight),
            actions = listOf(
                ToastAction({ tr("send", "Send to chat") }) { ChatScreen.openWith(listOf(shot.path)) },
                ToastAction({ tr("open", "Open") }) { Platform.desktop.openPath(shot.path) },
                ToastAction({ tr("copy", "Copy") }) { ScreenshotLibrary.copyToClipboard(shot) },
                ToastAction(
                    { if (ScreenshotLibrary.isFavorite(shot)) tr("unfavorite", "Unstar") else tr("favorite", "Star") },
                    dismisses = false,
                ) { ScreenshotLibrary.toggleFavorite(shot) },
                ToastAction({ tr("view", "View") }) { ScreenshotsScreen.open(focus = shot) },
            ),
        )
    }
}
