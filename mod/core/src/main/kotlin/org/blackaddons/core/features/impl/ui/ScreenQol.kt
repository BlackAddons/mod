package org.blackaddons.core.features.impl.ui

import org.blackaddons.api.event.render.CommandConfirmationEvent
import org.blackaddons.api.event.render.DisconnectConfirmationEvent
import org.blackaddons.api.event.render.FireOverlayRenderEvent
import org.blackaddons.api.event.render.LinkConfirmationEvent
import org.blackaddons.api.event.render.NightVisionRenderEvent
import org.blackaddons.api.event.render.StatusEffectsRenderEvent
import org.blackaddons.api.event.render.ToastAddEvent
import org.blackaddons.api.event.render.ToastType
import org.blackaddons.api.feature.Feature

object ScreenQol : Feature(
    description = "Quality of life adjustments for screen overlays, status effects, and system toasts.",
    name = "Screen QoL",
) {
    val removeFireOverlay by boolean("Remove Fire Overlay", default = false)
        .withDescription("Removes the fiery screen overlay when your player is on fire.")

    val hideStatusEffects by boolean("Hide Status Effects", default = false)
        .withDescription("Hides status potion effect icons on HUD and in inventory screens.")

    val disableNightVision by boolean("Disable Night Vision", default = false)
        .withDescription("Renders the world as if you didn't have the Night Vision effect. Client-side only.")

    val disableTutorialToasts by boolean("Disable Tutorial Toasts", default = true)
        .withDescription("Suppresses Minecraft tutorial toasts from popping up.")

    val disableUnsecureChatToast by boolean("Disable Unsecure Chat Toast", default = true)
        .withDescription("Suppresses the unverified or unsecure chat toast warning on multiplayer servers.")

    val disableCommandConfirmation by boolean("Disable Command Confirmation", default = true)
        .withDescription("Suppresses the confirmation prompt when clicking a command in chat.")

    val disableLinkConfirmation by boolean("Disable Link Confirmation", default = true)
        .withDescription("Suppresses the confirmation prompt when clicking a website link in chat.")

    val confirmDisconnect by boolean("Confirm Disconnect", default = false)
        .withDescription("Ask before leaving a server, in a small card under the Disconnect button.")

    override fun init() {
        register<FireOverlayRenderEvent> {
            if (removeFireOverlay.value) {
                event.cancelled = true
            }
        }

        register<StatusEffectsRenderEvent> {
            if (hideStatusEffects.value) {
                event.cancelled = true
            }
        }

        register<NightVisionRenderEvent> {
            if (disableNightVision.value) {
                event.cancelled = true
            }
        }

        register<ToastAddEvent> {
            if (disableTutorialToasts.value && event.type == ToastType.TUTORIAL) {
                event.cancelled = true
            }
            if (disableUnsecureChatToast.value && event.type == ToastType.UNSECURE_SERVER_WARNING) {
                event.cancelled = true
            }
        }

        register<CommandConfirmationEvent> {
            if (disableCommandConfirmation.value) {
                event.cancelled = true
            }
        }

        register<LinkConfirmationEvent> {
            if (disableLinkConfirmation.value || disableCommandConfirmation.value) {
                event.cancelled = true
            }
        }

        register<DisconnectConfirmationEvent> {
            if (confirmDisconnect.value) {
                event.confirm = true
            }
        }
    }
}
