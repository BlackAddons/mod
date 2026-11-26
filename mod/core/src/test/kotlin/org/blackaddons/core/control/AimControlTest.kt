package org.blackaddons.core.control

import org.blackaddons.api.player.*
import org.blackaddons.api.world.Vec3d
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Random
import kotlin.math.hypot

class AimControlTest {

    private class FakePort : ViewPort {
        override var inWorld = true
        override var screenOpen = false
        override var viewYaw = 0f
        override var viewPitch = 0f
        override var silentYaw: Float? = null
        override var silentPitch: Float? = null
        override var turnStep = 0f
        override var eyePos = Vec3d(0.0, 0.0, 0.0)
        override var eyeVelocity = Vec3d(0.0, 0.0, 0.0)
        override var cursorX = 0.0
        override var cursorY = 0.0
        var clears = 0

        override fun setView(yaw: Float, pitch: Float) {
            viewYaw = yaw
            viewPitch = pitch
        }

        override fun setSilent(yaw: Float, pitch: Float) {
            silentYaw = yaw
            silentPitch = pitch
        }

        override fun clearSilent() {
            if (silentYaw != null) clears++
            silentYaw = null
            silentPitch = null
        }

        override fun silentToView() {
            setView(silentYaw ?: return, silentPitch ?: viewPitch)
            silentYaw = null
            silentPitch = null
        }

        override fun anglesTo(point: Vec3d): Pair<Float, Float> = point.x.toFloat() to point.y.toFloat()

        override fun execute(block: () -> Unit) = block()
    }

    private val port = FakePort()
    private var now = 0L
    private val aim = AimControl(port, Random(1), clock = { now })

    // Straight turns, so landing points are predictable.
    private val straight = RotationParams(speed = 10f, humanize = false, smoothness = 0f)

    private fun frames(count: Int) = repeat(count) {
        now += 50_000_000L
        aim.onFrame(1f)
    }

    @Test
    fun `a silent aim turns the server rotation and leaves the camera alone`() {
        val lease = aim.request("a", silent = true)
        lease.turnTo(30f, 10f, straight)
        frames(20)

        assertTrue(lease.atTarget)
        assertEquals(30f, port.silentYaw!!, 0.01f)
        assertEquals(10f, port.silentPitch!!, 0.01f)
        assertEquals(0f, port.viewYaw)

        lease.release()
        assertNull(port.silentYaw)
    }

    @Test
    fun `a camera aim turns the camera`() {
        val lease = aim.request("a")
        lease.turnTo(-45f, 20f, straight)
        frames(20)
        assertEquals(-45f, port.viewYaw, 0.01f)
        assertNull(port.silentYaw)
    }

    @Test
    fun `a second owner waits, and its queued turn runs once it is granted`() {
        val first = aim.request("a", silent = true)
        val second = aim.request("b", silent = true)
        second.turnTo(60f, 0f, straight)
        assertEquals(LeaseState.WAITING, second.state)
        assertTrue(second.isTurning, "a queued turn counts as turning, so nobody clicks early")
        frames(20)
        assertEquals(0f, port.silentYaw!!, 0.01f, "the waiting turn must not move anything")

        first.release()
        assertTrue(second.isActive)
        frames(20)
        assertEquals(60f, port.silentYaw!!, 0.01f)
        assertEquals(0, port.clears, "the silent rotation carries over from one silent aim to the next")
    }

    @Test
    fun `high priority takes over at once without dropping the silent rotation`() {
        val normal = aim.request("feature", silent = true)
        normal.turnTo(30f, 0f, straight)
        frames(20)
        val high = aim.request("align", silent = true, priority = AimPriority.HIGH)
        assertEquals(LeaseState.REVOKED, normal.state)
        assertTrue(high.isActive)
        assertEquals(0, port.clears)
        assertEquals(30f, port.silentYaw!!, 0.01f)
    }

    @Test
    fun `ease hands the server rotation back gradually, then serves the next in line`() {
        val lease = aim.request("a", silent = true)
        lease.set(90f, 0f)
        val waiting = aim.request("b", silent = true)

        lease.release(HandBack.Ease(20f))
        assertTrue(lease.isTurning, "an aim easing back still counts as turning")
        assertEquals(LeaseState.WAITING, waiting.state)

        aim.onTick()
        assertEquals(70f, port.silentYaw!!, 0.01f)
        repeat(10) { aim.onTick() }
        assertFalse(lease.isTurning)
        assertTrue(waiting.isActive)
        assertEquals(1, port.clears)
    }

    @Test
    fun `ease follows the cursor as it moves`() {
        val lease = aim.request("a", silent = true)
        lease.set(90f, 30f)
        lease.release(HandBack.Ease(10f))
        aim.onTick()
        port.viewYaw = -60f
        val before = port.silentYaw!! to port.silentPitch!!
        aim.onTick()
        val moved = hypot(port.silentYaw!! - before.first, port.silentPitch!! - before.second)
        assertEquals(10f, moved, 0.01f)
        assertTrue(port.silentYaw!! < before.first, "heading for the new cursor")
    }

    @Test
    fun `the same owner coming back takes over the hand back where it stands`() {
        val lease = aim.request("a", silent = true)
        lease.set(90f, 0f)
        lease.release(HandBack.Ease(20f))
        aim.onTick()
        val other = aim.request("b", silent = true)
        val again = aim.request("a", silent = true)
        assertTrue(again.isActive)
        assertEquals(LeaseState.WAITING, other.state)
        assertEquals(70f, port.silentYaw!!, 0.01f)
        assertEquals(0, port.clears)
    }

    @Test
    fun `keep view moves the camera to the silent aim`() {
        val lease = aim.request("a", silent = true)
        lease.set(45f, 15f)
        lease.release(HandBack.KeepView)
        assertEquals(45f, port.viewYaw)
        assertEquals(15f, port.viewPitch)
        assertNull(port.silentYaw)
    }

    @Test
    fun `a silent aim giving way to a camera aim drops the silent rotation`() {
        val silent = aim.request("a", silent = true)
        silent.set(45f, 0f)
        val camera = aim.request("a")
        assertEquals(LeaseState.RELEASED, silent.state)
        assertTrue(camera.isActive)
        assertNull(port.silentYaw)
    }

    @Test
    fun `a camera aim taking over a hand back drops the half eased silent rotation`() {
        for (owner in listOf("a", "align")) {
            val lease = aim.request("a", silent = true)
            lease.set(90f, 0f)
            lease.release(HandBack.Ease(20f))
            aim.onTick()
            assertEquals(70f, port.silentYaw!!, 0.01f)

            val priority = if (owner == "a") AimPriority.NORMAL else AimPriority.HIGH
            val camera = aim.request(owner, priority = priority)
            assertTrue(camera.isActive, owner)
            assertNull(port.silentYaw, "$owner: the server must follow the camera, not a frozen half-eased rotation")
            camera.turnTo(-30f, 0f, straight)
            frames(20)
            assertEquals(-30f, port.viewYaw, 0.01f)
            assertNull(port.silentYaw)
            camera.release()
        }
    }

    @Test
    fun `a world change revokes every aim and drops the silent rotation`() {
        val lease = aim.request("a", silent = true)
        lease.set(45f, 0f)
        val waiting = aim.request("b")
        aim.onWorldChange()
        assertEquals(LeaseState.REVOKED, lease.state)
        assertEquals(LeaseState.REVOKED, waiting.state)
        assertNull(port.silentYaw)
    }

    @Test
    fun `nothing turns while a screen is open`() {
        val lease = aim.request("a")
        port.screenOpen = true
        lease.turnTo(30f, 0f, straight)
        frames(20)
        assertEquals(0f, port.viewYaw)
    }

    @Test
    fun `a nudge turns a settled aim at once and refuses a turning one`() {
        val lease = aim.request("a", silent = true)
        lease.turnTo(30f, 10f, straight)
        assertFalse(aim.nudge(1f))

        frames(20)
        assertTrue(aim.nudge(1f))
        assertEquals(31f, port.silentYaw!!, 0.01f)
        assertEquals(10f, port.silentPitch!!, 0.01f)
    }

    @Test
    fun `a hold keeps a released silent rotation until the next tick`() {
        val dropped = aim.request("a", silent = true)
        dropped.turnTo(30f, 0f, straight)
        frames(20)
        aim.holdThroughTick()
        dropped.release(HandBack.Discard)
        assertEquals(30f, port.silentYaw!!, 0.01f)
        aim.onTick()
        assertNull(port.silentYaw)

        val kept = aim.request("b", silent = true)
        kept.turnTo(40f, 0f, straight)
        frames(20)
        aim.holdThroughTick()
        kept.release(HandBack.KeepView)
        assertEquals(0f, port.viewYaw)
        aim.onTick()
        assertEquals(40f, port.viewYaw, 0.01f)
    }

    @Test
    fun `a Blackum turn lands on the target in whole mouse steps, then holds`() {
        port.turnStep = 0.15f
        val lease = aim.request("a")
        lease.turnToPoint(Vec3d(40.0, 10.0, 0.0), RotationParams(blackum = BlackumAim(targetRadius = 2f)))
        var yaw = port.viewYaw
        var pitch = port.viewPitch
        repeat(400) {
            now += 8_000_000L
            aim.onFrame(0.16f)
            assertEquals(0.0, wholeStepError(port.viewYaw - yaw), 1e-3)
            assertEquals(0.0, wholeStepError(port.viewPitch - pitch), 1e-3)
            yaw = port.viewYaw
            pitch = port.viewPitch
        }

        assertTrue(lease.atTarget)
        assertFalse(lease.isTurning)
        assertEquals(40f, port.viewYaw, 2f)
        assertEquals(10f, port.viewPitch, 2f)
    }

    @Test
    fun `Blackum reacts in 10 to 30 ms to an expected target and about 150 ms to a new one`() {
        fun reactions(mode: ReactionMode): List<Double> {
            val random = Random(7)
            return List(400) { BlackumMotion(BlackumAim(reaction = mode), random).reactTime }.sorted()
        }

        assertTrue(reactions(ReactionMode.EXPECTED).all { it in 0.010..0.030 })
        val normal = reactions(ReactionMode.NORMAL)
        assertTrue(normal.first() > 0.08)
        assertEquals(0.15, normal[normal.size / 2], 0.01)
    }

    @Test
    fun `moving the mouse during a Blackum flick stops it, then the aim corrects from there`() {
        port.turnStep = 0.15f
        val steady = AimHand(tremorStrength = 0f, drift = 0f)
        val lease = aim.request("a")
        lease.turnToPoint(Vec3d(40.0, 10.0, 0.0), RotationParams(blackum = BlackumAim(targetRadius = 2f, hand = steady)))
        fun frame() {
            now += 8_000_000L
            aim.onFrame(0.16f)
        }
        repeat(15) { frame() }
        assertTrue(lease.isTurning)

        port.viewYaw += 3f
        val bumped = port.viewYaw
        frame()
        assertTrue(port.viewYaw != bumped, "a view change without the mouse moving must not stop the flick")

        port.cursorX -= 100.0
        port.viewYaw -= 15f
        val moved = port.viewYaw to port.viewPitch
        repeat(2) { frame() }
        assertEquals(moved, port.viewYaw to port.viewPitch, "the old flick must not carry on over the player's move")

        repeat(400) { frame() }
        assertTrue(lease.atTarget)
        assertEquals(40f, port.viewYaw, 2f)
        assertEquals(10f, port.viewPitch, 2f)
    }

    private fun wholeStepError(delta: Float): Double {
        val counts = delta / port.turnStep.toDouble()
        return counts - Math.round(counts)
    }
}
