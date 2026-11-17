package org.blackaddons.core.features.impl.dev

import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.util.dev.DevFlags

internal object DevMode : Feature("Developer diagnostics — extra logging, event tracing, exception rethrow.") {

    private val verbose by boolean("Verbose Logging", default = false)
        .withDescription("Emit extra [dev] info-level logs from internal subsystems")

    private val rethrow by boolean("Rethrow Listener Exceptions", default = true)
        .withDescription("Surface event-listener bugs immediately instead of swallowing them. " +
            "Forced on automatically in a Fabric dev runtime regardless of this toggle")

    private val logDispatch by boolean("Log Event Dispatch", default = false)
        .withDescription("Log every EventBus.post — noisy, use only when debugging dispatch")

    override fun init() {
        // Synced again each tick, instead of a Setting onChange hook.
        register<ClientTickEvent.Post> { sync() }
    }

    override fun onEnable() {
        super.onEnable()
        sync()
    }

    override fun onDisable() {
        super.onDisable()
        clear()
    }

    private fun sync() {
        DevFlags.verboseLogging = enabled && verbose.value
        DevFlags.rethrowListenerExceptions = enabled && rethrow.value
        DevFlags.logEventDispatch = enabled && logDispatch.value
    }

    private fun clear() {
        DevFlags.verboseLogging = false
        DevFlags.rethrowListenerExceptions = false
        DevFlags.logEventDispatch = false
    }
}
