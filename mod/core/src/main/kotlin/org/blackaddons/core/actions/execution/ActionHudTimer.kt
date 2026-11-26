package org.blackaddons.core.actions.execution

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

internal object ActionHudTimer : ActionHudText("Timer")

internal object ActionTextOverlay : ActionHudText("")

internal open class ActionHudText(private val defaultName: String) {
    private var endsAt: TimeSource.Monotonic.ValueTimeMark? = null

    var name: String = defaultName
        private set

    val isActive: Boolean get() = remainingSeconds() > 0.0

    fun start(name: String, seconds: Float) {
        if (!seconds.isFinite() || seconds <= 0f) {
            reset()
            return
        }
        this.name = name.trim().ifEmpty { defaultName }
        endsAt = TimeSource.Monotonic.markNow() + seconds.toDouble().seconds
    }

    fun remainingSeconds(): Double {
        val endsAt = endsAt ?: return 0.0
        return (endsAt - TimeSource.Monotonic.markNow()).coerceAtLeast(Duration.ZERO).toDouble(DurationUnit.SECONDS)
    }

    fun reset() {
        name = defaultName
        endsAt = null
    }
}
