package org.blackaddons.api.event.input

import org.blackaddons.api.event.Event
import org.blackaddons.api.player.MoveKey
import org.blackaddons.api.player.MovementHost
import java.util.EnumSet

/**
 * The player's own movement keys, every tick on the client thread. Listeners may change them, for example to keep
 * walking where the camera looks while the server yaw is silent. To move the player, take a [MovementHost.drive]:
 * drives and holds apply after every listener.
 */
class MovementInputEvent(
    var forward: Boolean,
    var backward: Boolean,
    var left: Boolean,
    var right: Boolean,
    var jump: Boolean,
    var shift: Boolean,
    var sprint: Boolean,
    var forwardImpulse: Float,
    var leftImpulse: Float,
) : Event() {
    val pressed: Set<MoveKey>
        get() = EnumSet.noneOf(MoveKey::class.java).apply {
            if (forward) add(MoveKey.FORWARD)
            if (backward) add(MoveKey.BACKWARD)
            if (left) add(MoveKey.LEFT)
            if (right) add(MoveKey.RIGHT)
            if (jump) add(MoveKey.JUMP)
            if (shift) add(MoveKey.SNEAK)
            if (sprint) add(MoveKey.SPRINT)
        }

    /** Sets exactly [keys] down, with the impulses to match. */
    fun press(keys: Set<MoveKey>) {
        forward = MoveKey.FORWARD in keys
        backward = MoveKey.BACKWARD in keys
        left = MoveKey.LEFT in keys
        right = MoveKey.RIGHT in keys
        jump = MoveKey.JUMP in keys
        shift = MoveKey.SNEAK in keys
        sprint = MoveKey.SPRINT in keys
        matchImpulses()
    }

    /** Sets [key] down too. */
    fun press(key: MoveKey) {
        when (key) {
            MoveKey.FORWARD -> forward = true
            MoveKey.BACKWARD -> backward = true
            MoveKey.LEFT -> left = true
            MoveKey.RIGHT -> right = true
            MoveKey.JUMP -> jump = true
            MoveKey.SNEAK -> shift = true
            MoveKey.SPRINT -> sprint = true
        }
        matchImpulses()
    }

    /** Releases every movement key, sprint included. */
    fun release() = press(emptySet())

    private fun matchImpulses() {
        forwardImpulse = impulse(forward, backward)
        leftImpulse = impulse(left, right)
    }

    private fun impulse(positive: Boolean, negative: Boolean): Float = when {
        positive == negative -> 0f
        positive -> 1f
        else -> -1f
    }
}
