package org.blackaddons.core.log

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * `BLACKADDONS`, or `BLACKADDONS:<area>` for a subsystem. ERROR/WARN when something failed (WARN if it recovers). INFO
 * only for the startup summary and for output a debug toggle asked for. DEBUG for the rest.
 */
object Log {
    internal const val NAME = "BLACKADDONS"

    @JvmField
    val main: Logger = LoggerFactory.getLogger(NAME)

    @JvmStatic
    fun area(area: String): Logger = LoggerFactory.getLogger("$NAME:$area")
}
