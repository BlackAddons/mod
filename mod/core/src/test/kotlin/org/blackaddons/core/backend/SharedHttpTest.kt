package org.blackaddons.core.backend

import org.blackaddons.api.gui.ScreenHost
import org.blackaddons.api.testing.TestPlatform
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SharedHttpTest {

    @Test
    fun `blocking calls refuse the client thread before touching the network`() {
        TestPlatform.screenHost = object : ScreenHost {
            override val anyScreenOpen = false
            override val isClientThread = true
            override fun show() = Unit
            override fun hide() = Unit
        }
        assertThrows(IllegalStateException::class.java) { SharedHttp.getBlocking("http://127.0.0.1:9/") }
        assertThrows(IllegalStateException::class.java) { SharedHttp.bytesBlocking("http://127.0.0.1:9/") }
    }
}
