package org.blackaddons.core.ui.screen

import org.blackaddons.api.Platform
import org.blackaddons.api.chat.ChatMessage
import org.blackaddons.api.ui.layout.Align
import org.blackaddons.api.ui.layout.column
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.ui.screen.ModalPanel
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.ui.theme.Fonts
import org.blackaddons.api.ui.theme.TextStyle
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.ui.widget.Button
import org.blackaddons.api.ui.widget.text.TextInput
import org.blackaddons.api.util.I18n
import org.blackaddons.core.backend.auth.Elevation
import org.blackaddons.core.chat.Brand
import org.blackaddons.core.chat.brand

/**
 * 2FA code entry. A window, not a command arg, so a code without its slash never goes to public chat. Takes TOTP or
 * `xxxxx-xxxxx` recovery codes. The backend normalizes the input.
 */
internal class StaffSignInModal : ModalPanel(I18n.tr("blackaddons.elevation.title")) {

    // UiManager units, because `Platform.window.scaledWidth` is silently wrong here.
    override val panelWidth: Float get() = fitWidth(380f, min = 300f)
    override val panelHeight: Float get() = 210f


    private val codeInput = widgets.add(
        TextInput(
            placeholder = I18n.tr("blackaddons.elevation.placeholder"),
            maxLength = MAX_CODE_LENGTH,
            charFilter = { it.isLetterOrDigit() || it == '-' },
        ).apply {
            focused = true
            onEnter = { submit() }
        },
    )

    private val cancelBtn = widgets.add(Button(I18n.tr("blackaddons.elevation.cancel")) { UiManager.pop() })

    private val confirmBtn = widgets.add(
        Button(I18n.tr("blackaddons.elevation.confirm")) { submit() }.apply { accent = true },
    )

    private var remember = true

    private var busy = false
    private var errorKey: String? = null

    private fun submit() {
        val code = codeInput.text.trim()
        if (busy || code.isEmpty()) return
        busy = true
        errorKey = null
        Elevation.elevate(code, remember) { outcome -> Platform.scheduler.schedule(0) { finish(outcome) } }
    }

    private fun finish(outcome: Elevation.Outcome) {
        busy = false
        when (outcome) {
            Elevation.Outcome.RAISED -> raised()
            Elevation.Outcome.BAD_CODE -> failed("blackaddons.elevation.bad_code")
            Elevation.Outcome.THROTTLED -> failed("blackaddons.elevation.throttled")
            // Retyping can't fix it. Close, and let the chat line link to 2FA setup.
            Elevation.Outcome.NOT_POSSIBLE -> {
                if (UiManager.current === this) UiManager.pop()
                Elevation.postEnable2fa()
            }
            Elevation.Outcome.FAILED -> failed("blackaddons.elevation.failed")
        }
    }

    /** Codes are single-use, so the field gets cleared. */
    private fun failed(key: String) {
        errorKey = key
        codeInput.text = ""
    }

    private fun raised() {
        // Something else might be on top now. The elevation still gets announced.
        if (UiManager.current === this) UiManager.pop()
        Platform.chat.local(ChatMessage.build {
            brand()
            append(I18n.tr("blackaddons.elevation.raised"), Brand.OK)
        })
    }

    override fun renderContent(mouseX: Float, mouseY: Float) {
        val w = contentW
        confirmBtn.enabled = !busy && codeInput.text.isNotBlank()
        confirmBtn.label = I18n.tr(if (busy) "blackaddons.elevation.working" else "blackaddons.elevation.confirm")

        val layout = column(spacing = Theme.PADDING_SMALL, width = w, height = contentH) {
            text(I18n.tr("blackaddons.elevation.code_label"), TextStyle.SMALL_MUTED_MEDIUM)
            cell(w, 22f) { cx, cy -> codeInput.render(cx, cy, w, 22f, mouseX, mouseY) }

            cell(w, 16f) { cx, cy ->
                widgets.clickable(cx, cy + 1f, TOGGLE_W, TOGGLE_H) { remember = !remember }
                Render.toggleSwitch(cx, cy + 1f, TOGGLE_W, TOGGLE_H, if (remember) 1f else 0f)
                Render.text(
                    I18n.tr("blackaddons.elevation.remember"),
                    cx + TOGGLE_W + 8f,
                    Render.centerY(cy, 16f, TextStyle.SMALL_SECONDARY),
                    TextStyle.SMALL_SECONDARY,
                )
            }

            val message = errorKey
            if (message != null) {
                text(I18n.tr(message), Theme.FONT_SMALL, Theme.DANGER, Fonts.regular)
            } else {
                text(I18n.tr("blackaddons.elevation.hint"), TextStyle.SMALL_MUTED)
            }

            flexSpacer()
            row(spacing = Theme.PADDING, align = Align.CENTER, width = w) {
                flexSpacer()
                cell(BUTTON_W, 24f) { cx, cy -> cancelBtn.render(cx, cy, BUTTON_W, 24f, mouseX, mouseY) }
                cell(BUTTON_W, 24f) { cx, cy -> confirmBtn.render(cx, cy, BUTTON_W, 24f, mouseX, mouseY) }
            }
        }

        layout.draw(contentX, contentY)
    }


    companion object {
        private const val MAX_CODE_LENGTH = 11
        private const val TOGGLE_W = 28f
        private const val TOGGLE_H = 14f
        private const val BUTTON_W = 96f

        fun open() = UiManager.open(StaffSignInModal())
    }
}
