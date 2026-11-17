package org.blackaddons.api

import org.blackaddons.api.testing.TestPlatform
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PlatformTest {

    @Test
    fun `the platform binds once and nothing can rebind it`() {
        TestPlatform.blackaddonsVersion = "bound"

        assertThrows<IllegalStateException> { Platform.bind(TestPlatform) }
        assertSame("bound", Platform.blackaddonsVersion)
    }
}
