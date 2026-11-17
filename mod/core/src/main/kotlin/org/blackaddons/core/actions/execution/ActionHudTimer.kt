package org.blackaddons.core.actions.execution
internal object ActionHudTimer : ActionHudText("Timer")

internal object ActionTextOverlay : ActionHudText("")

internal open class ActionHudText(private val defaultName: String) {
    private var startedAtMs = 0L
    private var durationMs = 0L

    var name: String = defaultName
        private set

    val isActive: Boolean get() = remainingSeconds() > 0.0

    fun start(name: String, seconds: Float, nowMs: Long = System.currentTimeMillis()) {
        if (!seconds.isFinite() || seconds <= 0f) {
            reset()
            return
        }
        this.name = name.trim().ifEmpty { defaultName }
        startedAtMs = nowMs
        durationMs = (seconds * 1_000).toLong()
    }

    fun remainingSeconds(nowMs: Long = System.currentTimeMillis()): Double {
        if (durationMs <= 0L) return 0.0
        val elapsedMs = (nowMs - startedAtMs).coerceAtLeast(0L)
        return (durationMs - elapsedMs).coerceAtLeast(0L) / 1_000.0
    }

    fun reset() {
        name = defaultName
        startedAtMs = 0L
        durationMs = 0L
    }
}
