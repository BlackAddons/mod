package org.blackaddons.core.log

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Slf4jAddonLoggersTest {

    @Test
    fun `addons log under their loader id, built-in ones as the mod`() {
        assertEquals("BLACKADDONS", Slf4jAddonLoggers.nameFor("blackaddons"))
        assertEquals("BLACKADDONS-auth", Slf4jAddonLoggers.nameFor("blackaddons-auth"))
        assertEquals("BLACKADDONS-skyhelper", Slf4jAddonLoggers.nameFor("skyhelper"))
        assertEquals("BLACKADDONS-unregistered", Slf4jAddonLoggers.nameFor(null))
    }
}
