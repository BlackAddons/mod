package org.blackaddons.cheat.actions

import com.google.gson.Gson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CheatStepsTest {

    private val gson = Gson()

    @Test
    fun `steps saved before the step types moved here keep every field`() {
        assertEquals(
            AlignStep.Params(
                useCoordinates = true, targetX = 4.5, targetZ = -8.5, durationSeconds = 2f, lookAfterAlign = true,
                alignPostYaw = 45f, alignPostPitch = 10f, useLookAfterCoords = true, alignLookAtX = 1.0, alignLookAtY = 2.0,
                alignLookAtZ = 3.0, dontWaitForPerfectAlign = true,
            ),
            gson.fromJson(
                """{"type":"ALIGN","useCoordinates":true,"targetX":4.5,"targetZ":-8.5,"durationSeconds":2.0,"lookAfterAlign":true,
                "alignPostYaw":45.0,"alignPostPitch":10.0,"useLookAfterCoords":true,"alignLookAtX":1.0,"alignLookAtY":2.0,
                "alignLookAtZ":3.0,"dontWaitForPerfectAlign":true}""",
                AlignStep.Params::class.java,
            ),
        )
        assertEquals(
            RotateStep.Params(useCoordinates = true, targetY = 70.0, yaw = 90f, pitch = -5f, rotationSpeed = 20f, lookAtSeconds = 1f, instaSnap = true),
            gson.fromJson(
                """{"type":"ROTATE","useCoordinates":true,"targetY":70.0,"yaw":90.0,"pitch":-5.0,"rotationSpeed":20.0,"lookAtSeconds":1.0,"instaSnap":true}""",
                RotateStep.Params::class.java,
            ),
        )
        assertEquals(
            MoveKeybindsStep.Params(listOf("forward", "jump"), 0.5f),
            gson.fromJson("""{"type":"MOVE_KEYBINDS","movementKeybinds":["forward","jump"],"durationSeconds":0.5}""", MoveKeybindsStep.Params::class.java),
        )
        assertEquals(SwitchSlotStep.Params(4), gson.fromJson("""{"slotIndex":4}""", SwitchSlotStep.Params::class.java))
        assertEquals(PressKeybindStep.Params("key.keyboard.f5", 1f), gson.fromJson("""{"message":"key.keyboard.f5","durationSeconds":1.0}""", PressKeybindStep.Params::class.java))
        assertEquals(HeldStep.Params(0.25f), gson.fromJson("""{"type":"USE_ITEM","durationSeconds":0.25}""", HeldStep.Params::class.java))
    }

    @Test
    fun `a missing field reads as the old default`() {
        assertEquals(RotateStep.Params(), gson.fromJson("{}", RotateStep.Params::class.java))
        assertEquals(15f, RotateStep.Params().rotationSpeed)
        assertEquals(null, gson.fromJson("{}", AlignStep.Params::class.java).dontWaitForPerfectAlign)
    }
}
