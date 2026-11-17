package org.blackaddons.core.actions.execution
import org.blackaddons.api.Platform
import org.blackaddons.api.ui.toast.Toasts
import org.blackaddons.api.util.colorize
import org.blackaddons.api.util.secondsToTicks
import org.blackaddons.core.actions.ActionFeedbackProvider

internal object ActionFeedbackDispatcher {

    fun dispatch(
        feedback: ActionFeedbackProvider,
        groups: Array<String>? = null,
        defaultNotificationTitle: String = "Action Triggered",
        player: String? = null,
    ) {
        if (feedback.soundId.isNotBlank()) {
            Platform.feedback.playSound(feedback.soundId, feedback.volume, feedback.pitch)
        }

        if (feedback.durationSeconds > 0f && feedback.title.isNotBlank()) {
            val title = ActionInterpolator.interpolate(feedback.title, groups, player)
            val subtitle = ActionInterpolator.interpolate(feedback.subtitle, groups, player)
            val stayTicks = feedback.durationSeconds.secondsToTicks.coerceAtLeast(10)
            Platform.feedback.showTitle(
                title.colorize(),
                subtitle.colorize().takeIf { it.isNotBlank() },
                fadeIn = 5,
                stay = stayTicks,
                fadeOut = 10,
            )
        }

        if (feedback.showNotification && feedback.notificationMessage.isNotBlank()) {
            val rawTitle = feedback.notificationTitle.ifBlank { defaultNotificationTitle }
            val nTitle = ActionInterpolator.interpolate(rawTitle, groups, player)
            val nMsg = ActionInterpolator.interpolate(feedback.notificationMessage, groups, player)
            Toasts.show(nTitle.colorize(), nMsg.colorize())
        }
    }
}
