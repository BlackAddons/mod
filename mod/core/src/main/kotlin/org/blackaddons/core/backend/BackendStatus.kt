package org.blackaddons.core.backend

import org.blackaddons.api.Platform
import org.blackaddons.api.chat.ChatMessage
import org.blackaddons.api.host.Cancellation
import org.blackaddons.api.ui.toast.ToastKind
import org.blackaddons.api.ui.toast.Toasts
import org.blackaddons.api.util.I18n
import org.blackaddons.core.chat.Brand
import org.blackaddons.core.chat.brand
import java.awt.Color

/**
 * Tracks whether the backend can serve us, so the mod can say so instead of looking broken. Gateway `SERVER_STATUS` and
 * HTTP 503s feed it. Called from network threads.
 */
internal object BackendStatus {

    private enum class State { OK, DEGRADED, UNSUPPORTED }

    @Volatile private var pushDegraded = false
    @Volatile private var httpDegraded = false
    @Volatile private var unsupported = false

    @Volatile private var announced = false

    // A lone refused request is usually a blip the next one clears; only an outage that lasts is worth a toast.
    private const val ANNOUNCE_DELAY_TICKS = 200
    private var pendingAnnounce: Cancellation? = null

    /** UNSUPPORTED wins over DEGRADED: a refused build stays refused after recovery. */
    private val state: State
        get() = when {
            unsupported -> State.UNSUPPORTED
            pushDegraded || httpDegraded -> State.DEGRADED
            else -> State.OK
        }

    val notice: String?
        get() = when (state) {
            State.OK -> null
            State.DEGRADED -> I18n.tr("blackaddons.backend.unavailable")
            State.UNSUPPORTED -> I18n.tr("blackaddons.backend.unsupported")
        }

    /** Has priority while a socket is up. */
    fun onServerStatus(degraded: Boolean) {
        pushDegraded = degraded
        settle()
    }

    /** The only signal for a player without a socket. */
    fun onUnavailable() {
        httpDegraded = true
        settle()
    }

    fun onReached() {
        if (!httpDegraded) return
        httpDegraded = false
        settle()
    }

    /** Below the supported version. Only a new jar clears it. Silent, because the update check tells the player. */
    fun onUnsupported() {
        unsupported = true
    }

    /**
     * Announces from the combined state, so one source alone can't declare recovery. An outage is only told once it
     * outlasts [ANNOUNCE_DELAY_TICKS], and only players who heard about it hear about the recovery. Synchronized,
     * because both sources are network threads.
     */
    @Synchronized
    private fun settle() {
        // The update check announces an unsupported build. Keep [announced] as it is.
        if (state == State.UNSUPPORTED) return

        if (state == State.DEGRADED) {
            if (announced || pendingAnnounce != null) return
            pendingAnnounce = Platform.scheduler.schedule(ANNOUNCE_DELAY_TICKS, ::announceIfStillDown)
            return
        }

        pendingAnnounce?.cancel()
        pendingAnnounce = null
        if (!announced) return
        announced = false
        say("blackaddons.backend.restored", Brand.OK, ToastKind.SUCCESS)
    }

    @Synchronized
    private fun announceIfStillDown() {
        pendingAnnounce = null
        if (state != State.DEGRADED || announced) return
        announced = true
        say("blackaddons.backend.unavailable", Brand.FAIL, ToastKind.ERROR)
    }

    private fun say(key: String, color: Color, kind: ToastKind) {
        val text = I18n.tr(key)
        Toasts.show(text, kind = kind)
        Platform.chat.local(ChatMessage.build {
            brand()
            append(text, color)
        })
    }
}
