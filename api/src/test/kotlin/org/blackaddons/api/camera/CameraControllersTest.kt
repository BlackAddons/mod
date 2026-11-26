package org.blackaddons.api.camera

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CameraControllersTest {

    private val freecam = Controller()
    private val perspective = Controller()

    @AfterEach
    fun releaseAll() {
        CameraControllers.release(freecam)
        CameraControllers.release(perspective)
    }

    @Test
    fun `taking the camera displaces the holder, which switching off does not undo`() {
        CameraControllers.register(freecam)
        CameraControllers.register(perspective)

        assertEquals(1, freecam.displaced)
        assertSame(perspective, CameraControllers.active())
    }

    @Test
    fun `only the holder can give the camera back`() {
        CameraControllers.register(freecam)
        CameraControllers.register(perspective)

        CameraControllers.release(freecam)
        assertSame(perspective, CameraControllers.active())

        CameraControllers.release(perspective)
        assertNull(CameraControllers.active())
    }

    @Test
    fun `taking it again is not a displacement`() {
        CameraControllers.register(freecam)
        CameraControllers.register(freecam)

        assertEquals(0, freecam.displaced)
    }

    // Like real features: switching off on displacement releases the camera.
    private class Controller : CameraController {
        var displaced = 0
        override var active = true
        override fun posX(tickDelta: Float) = 0.0
        override fun posY(tickDelta: Float) = 0.0
        override fun posZ(tickDelta: Float) = 0.0
        override fun rotYaw(tickDelta: Float) = 0f
        override fun rotPitch(tickDelta: Float) = 0f
        override val pullback = 0f
        override val hideHands = false
        override fun onTurn(deltaX: Double, deltaY: Double) = Unit

        override fun onDisplaced() {
            displaced++
            active = false
            CameraControllers.release(this)
        }
    }
}
