package org.blackaddons.core.log

import org.blackaddons.api.Addon
import org.blackaddons.api.host.AddonLogger
import org.blackaddons.api.host.AddonLoggers
import org.blackaddons.core.feature.FeatureManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

internal object Slf4jAddonLoggers : AddonLoggers {

    // The built-in addons in the fat jar share the mod's id, so they log as the mod itself.
    private const val HOST_MOD_ID = "blackaddons"

    private val cache = ConcurrentHashMap<String, AddonLogger>()

    override fun of(addon: Addon): AddonLogger = named(nameFor(FeatureManager.modIdOf(addon)))

    internal fun nameFor(modId: String?): String = when (modId) {
        null -> "${Log.NAME}-unregistered"
        HOST_MOD_ID -> Log.NAME
        else -> "${Log.NAME}-${modId.removePrefix("$HOST_MOD_ID-")}"
    }

    private fun named(name: String): AddonLogger = cache.computeIfAbsent(name) { Slf4jAddonLogger(it) }

    private class Slf4jAddonLogger(private val name: String) : AddonLogger {
        private val delegate: Logger = LoggerFactory.getLogger(name)

        override fun info(message: String, vararg args: Any?) = delegate.info(message, *args)
        override fun warn(message: String, vararg args: Any?) = delegate.warn(message, *args)
        override fun error(message: String, vararg args: Any?) = delegate.error(message, *args)
        override fun debug(message: String, vararg args: Any?) = delegate.debug(message, *args)
        override fun area(area: String): AddonLogger = named("$name:$area")
    }
}
