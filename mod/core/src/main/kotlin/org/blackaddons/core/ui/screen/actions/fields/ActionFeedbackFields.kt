package org.blackaddons.core.ui.screen.actions.fields
import org.blackaddons.api.Platform
import org.blackaddons.api.ui.layout.Align
import org.blackaddons.api.ui.layout.LayoutBuilder
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.Slider
import org.blackaddons.api.ui.widget.WidgetHost
import org.blackaddons.api.ui.widget.text.TextInput
import org.blackaddons.api.util.formatDecimals
import org.blackaddons.core.actions.ActionFeedbackProvider
import org.blackaddons.core.ui.screen.actions.actionTr

/** Sound, title and toast fields every action editor shares. Edits a copy: [readFrom] fills it, [writeTo] commits. */
internal class ActionFeedbackFields {

    private var toastEnabled = false

    private val soundInput = TextInput(placeholder = "minecraft:entity.experience_orb.pickup").apply {
        suggestions = { query ->
            val ids = Platform.feedback.soundIds()
            val lower = query.lowercase()
            if (lower.isEmpty()) ids.take(SUGGESTION_LIMIT)
            else ids.filter { it.contains(lower) }.take(SUGGESTION_LIMIT)
        }
        suggestionDisplay = { it.removePrefix("minecraft:") }
    }

    private val testSoundBtn = Button(actionTr("feedback.test", "Test")).apply {
        onClick = {
            val id = soundInput.text.trim().ifEmpty { "minecraft:entity.experience_orb.pickup" }
            Platform.feedback.playSound(id, volumeSlider.value.toFloat(), pitchSlider.value.toFloat())
        }
    }

    private val volumeSlider = Slider(min = 0.0, max = 1.0, value = 1.0, step = 0.05)
    private val pitchSlider = Slider(min = 0.5, max = 2.0, value = 1.0, step = 0.05)
    private val titleDurationSlider = Slider(min = 0.5, max = 10.0, value = 2.0, step = 0.5)

    private val titleInput = TextInput(placeholder = actionTr("feedback.title_placeholder", "Screen Title"))
    private val subtitleInput = TextInput(placeholder = actionTr("feedback.subtitle_placeholder", "Screen Subtitle"))
    private val toastTitleInput = TextInput(placeholder = actionTr("feedback.toast_title_placeholder", "Toast Notification Title"))
    private val toastMessageInput = TextInput(placeholder = actionTr("feedback.toast_message_placeholder", "Toast Notification Message"))

    private val toastToggleBtn = Button("") {
        toastEnabled = !toastEnabled
        updateLabels()
    }

    fun attachTo(host: WidgetHost) {
        listOf(
            soundInput, testSoundBtn, volumeSlider, pitchSlider, titleDurationSlider,
            titleInput, subtitleInput, toastTitleInput, toastMessageInput, toastToggleBtn,
        ).forEach { host.add(it) }
    }

    fun readFrom(source: ActionFeedbackProvider?) {
        soundInput.text = source?.soundId ?: ""
        volumeSlider.value = (source?.volume ?: 1.0f).toDouble()
        pitchSlider.value = (source?.pitch ?: 1.0f).toDouble()
        titleInput.text = source?.title ?: ""
        subtitleInput.text = source?.subtitle ?: ""
        titleDurationSlider.value = (source?.durationSeconds ?: 2.0f).toDouble()
        toastEnabled = source?.showNotification ?: false
        toastTitleInput.text = source?.notificationTitle ?: ""
        toastMessageInput.text = source?.notificationMessage ?: ""
        updateLabels()
    }

    fun writeTo(target: ActionFeedbackProvider) {
        target.soundId = soundInput.text.trim()
        target.volume = volumeSlider.value.toFloat()
        target.pitch = pitchSlider.value.toFloat()
        target.title = titleInput.text.trim()
        target.subtitle = subtitleInput.text.trim()
        target.durationSeconds = titleDurationSlider.value.toFloat()
        target.showNotification = toastEnabled
        target.notificationTitle = toastTitleInput.text.trim()
        target.notificationMessage = toastMessageInput.text.trim()
    }

    fun updateLabels() {
        toastToggleBtn.label = actionTr("feedback.toast", "Notification: %s", actionTr(if (toastEnabled) "on" else "off", if (toastEnabled) "ON" else "OFF"))
    }

    fun renderOverlays(mouseX: Float, mouseY: Float) = soundInput.renderSuggestions(mouseX, mouseY)

    fun section(builder: LayoutBuilder, formW: Float, mouseX: Float, mouseY: Float) = with(builder) {
        text(actionTr("feedback.sound_id", "Sound ID:"), TextStyle.SMALL_MUTED_MEDIUM)
        row(spacing = Theme.PADDING_SMALL, align = Align.CENTER, width = formW) {
            val inputW = formW - 56f
            cell(inputW, 22f) { cx, cy -> soundInput.render(cx, cy, inputW, 22f, mouseX, mouseY) }
            cell(48f, 22f) { cx, cy -> testSoundBtn.render(cx, cy, 48f, 22f, mouseX, mouseY) }
        }
        row(spacing = Theme.PADDING_SMALL, align = Align.CENTER, width = formW) {
            val colW = (formW - Theme.PADDING_SMALL) / 2f
            labelledSlider(actionTr("feedback.volume", "Volume"), volumeSlider.value.formatDecimals(2), colW) { cx, cy ->
                volumeSlider.render(cx, cy, colW, 16f, mouseX, mouseY)
            }
            labelledSlider(actionTr("feedback.pitch", "Pitch"), pitchSlider.value.formatDecimals(2), colW) { cx, cy ->
                pitchSlider.render(cx, cy, colW, 16f, mouseX, mouseY)
            }
        }

        text(actionTr("feedback.screen_title", "Screen Title:"), TextStyle.SMALL_MUTED_MEDIUM)
        cell(formW, 22f) { cx, cy -> titleInput.render(cx, cy, formW, 22f, mouseX, mouseY) }

        text(actionTr("feedback.screen_subtitle", "Screen Subtitle:"), TextStyle.SMALL_MUTED_MEDIUM)
        cell(formW, 22f) { cx, cy -> subtitleInput.render(cx, cy, formW, 22f, mouseX, mouseY) }

        text(actionTr("feedback.title_duration", "Title Duration: %ss", titleDurationSlider.value.formatDecimals(1)), TextStyle.SMALL_MUTED_MEDIUM)
        cell(formW, 16f) { cx, cy -> titleDurationSlider.render(cx, cy, formW, 16f, mouseX, mouseY) }

        text(actionTr("feedback.notification", "Notification:"), TextStyle.SMALL_MUTED_MEDIUM)
        row(spacing = Theme.PADDING_SMALL, align = Align.CENTER, width = formW) {
            val toggleW = toastToggleBtn.naturalWidth() + Theme.PADDING
            val inputW = formW - toggleW - Theme.PADDING_SMALL
            cell(toggleW, 22f) { cx, cy -> toastToggleBtn.render(cx, cy, toggleW, 22f, mouseX, mouseY) }
            cell(inputW, 22f) { cx, cy -> toastTitleInput.render(cx, cy, inputW, 22f, mouseX, mouseY) }
        }

        text(actionTr("feedback.notification_message", "Notification Message:"), TextStyle.SMALL_MUTED_MEDIUM)
        cell(formW, 22f) { cx, cy -> toastMessageInput.render(cx, cy, formW, 22f, mouseX, mouseY) }
    }

    private companion object {
        /** The sound list has thousands of entries. */
        const val SUGGESTION_LIMIT = 50
    }
}

internal fun LayoutBuilder.labelledSlider(label: String, value: String, w: Float, draw: (Float, Float) -> Unit) {
    cell(w, 28f) { cx, cy ->
        Render.text("$label: $value", cx, cy, TextStyle.SMALL_MUTED_MEDIUM)
        draw(cx, cy + 12f)
    }
}
