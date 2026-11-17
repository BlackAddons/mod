package org.blackaddons.api.host

import org.blackaddons.api.Addon

/**
 * Writes to `latest.log` under the addon's own name, so no addon can log as the host or as another addon. Get it
 * from [Addon.log]. Messages take SLF4J `{}` placeholders, and a trailing throwable is logged with its stack trace.
 */
interface AddonLogger {
    fun info(message: String, vararg args: Any?)
    fun warn(message: String, vararg args: Any?)
    fun error(message: String, vararg args: Any?)
    fun debug(message: String, vararg args: Any?)

    /** A logger for one part of the addon, named `<name>:<area>`. Cached. */
    fun area(area: String): AddonLogger
}

interface AddonLoggers {
    /** Named after the mod id the loader registered [addon] under. Works before `onLoad`. */
    fun of(addon: Addon): AddonLogger
}
