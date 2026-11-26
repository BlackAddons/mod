package org.blackaddons.core.backend

import org.blackaddons.api.Platform
import org.blackaddons.api.chat.ChatClick
import org.blackaddons.api.chat.ChatMessage
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.concurrent.Workers
import org.blackaddons.core.backend.auth.BackendSession
import org.blackaddons.core.backend.gateway.Gateway
import org.blackaddons.core.chat.Brand
import org.blackaddons.core.chat.brand
import org.blackaddons.core.update.UpdateCheck
import java.awt.Color
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The one "backend isn't answering" chat line, with a Retry link. An atomic latch makes sure failures from session,
 * cosmetics, gateway and presence only post it once. The next success resets the latch. `[Retry]` runs `/blackaddons
 * reauth`, which calls [retry].
 */
internal object BackendNotifier {

    /** No leading slash. */
    private const val REAUTH_COMMAND = "blackaddons reauth"

    private val notified = AtomicBoolean(false)
    private val retrying = AtomicBoolean(false)

    fun notifyAuthFailure() {
        if (notified.compareAndSet(false, true)) postFailure()
    }

    /** For callers with a more specific message (e.g. cosmetics using the cache). Only runs if nothing was posted. */
    fun notifyUnreachable(post: () -> Unit) {
        if (notified.compareAndSet(false, true)) post()
    }

    fun onReachable() {
        notified.set(false)
    }

    fun onAuthSuccess() = onReachable()

    // Not for a refused build: only a new jar helps there, a reauth fixes nothing.
    fun ensureConnected() {
        if (!Gateway.connected && !Gateway.unsupported) retry()
    }

    fun retry() {
        if (Gateway.unsupported) {
            if (!UpdateCheck.remindUnsupported()) chat("blackaddons.backend.unsupported", Brand.FAIL)
            return
        }
        if (!retrying.compareAndSet(false, true)) return
        chat("blackaddons.backend.retrying", null)
        Workers.start("reauth") {
            try {
                BackendSession.forget()
                if (BackendSession.validToken() != null) {
                    onReachable()
                    chat("blackaddons.backend.auth_ok", Brand.OK)
                    // The socket identified with the old token, or it's stuck in backoff.
                    Gateway.restart()
                } else {
                    // The failure path of validToken is debounced. A manual retry always gets an answer.
                    postFailure()
                }
            } finally {
                retrying.set(false)
            }
        }
    }

    /** Ignores the latch, because the player asked. */
    private fun postFailure() {
        Platform.chat.local(ChatMessage.build {
            brand()
            append(I18n.tr("blackaddons.backend.auth_failed") + " ", Brand.FAIL)
            append(
                I18n.tr("blackaddons.backend.retry"),
                Brand.OK, underlined = true,
                click = ChatClick.RunCommand(REAUTH_COMMAND),
                hover = I18n.tr("blackaddons.backend.retry_hover"),
            )
        })
    }

    private fun chat(key: String, color: Color?) {
        Platform.chat.local(ChatMessage.build {
            brand()
            append(I18n.tr(key), color ?: Brand.BODY)
        })
    }
}
