package org.blackaddons.core.backend

import org.blackaddons.api.util.dev.DevFileLogger
import org.blackaddons.api.util.dev.DevFlags
import org.blackaddons.core.log.Log
import org.slf4j.helpers.MessageFormatter

/**
 * Network log. DEBUG by default. With `DevMode -> Verbose Logging`, `[net]` lines go to the game log and to
 * `config/blackaddons/dev/logs/network.log`.
 */
internal object NetLog {

    private val file by lazy { DevFileLogger.named("network") }

    fun trace(message: String, vararg args: Any?) {
        if (!DevFlags.verboseLogging) {
            Log.main.debug(message, *args)
            return
        }
        Log.main.info("[net] $message", *args)
        file.write(MessageFormatter.arrayFormat(message, args).message)
    }
}
