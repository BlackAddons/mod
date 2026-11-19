package org.blackaddons.cheat.actions

import org.blackaddons.api.Platform
import org.blackaddons.api.player.Aim
import org.blackaddons.api.player.AimPriority
import org.blackaddons.api.player.HandBack
import org.blackaddons.api.util.secondsToTicks

/** An action run's HIGH aim lease: silent during No Rotate or silent Align, camera otherwise. */
internal object ActionAim {

    private var lease: Aim? = null

    var silentSpan = false
        private set

    private var lookTicks = 0

    val isBusy: Boolean get() = lease?.isTurning == true || lookTicks > 0

    val isHandingBack: Boolean get() = lease?.let { it.isEnded && it.isTurning } == true

    fun lease(silent: Boolean = silentSpan): Aim =
        lease?.takeIf { !it.isEnded && it.silent == silent }
            ?: Platform.rotation.request(this, silent = silent, priority = AimPriority.HIGH).also { lease = it }

    fun beginSilentSpan() {
        silentSpan = true
        lease(silent = true)
    }

    fun endSilentSpan(handBack: HandBack) {
        silentSpan = false
        lease?.release(handBack)
    }

    /** So an idle camera aim doesn't block other features. */
    fun releaseIfIdle() {
        val current = lease ?: return
        if (!silentSpan && current.isActive && !current.silent && !isBusy) current.release()
    }

    fun look(seconds: Float) {
        lookTicks = seconds.secondsToTicks
    }

    // Blackum aims track the point on their own.
    fun tick() {
        if (lookTicks <= 0) return
        val current = lease?.takeIf { it.isActive } ?: return endLook()
        if (current.isTurning) return
        lookTicks--
    }

    fun release() {
        silentSpan = false
        endLook()
        lease?.release()
    }

    private fun endLook() {
        lookTicks = 0
    }
}
