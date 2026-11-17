package org.blackaddons.api.player

import org.blackaddons.api.world.BlockPos3
import java.util.UUID

/**
 * Synthetic clicks, crosshair reads and hotbar swaps; pairs with [RotationHost]. Every click goes through
 * [submit] and the host picks the moment (the tick's click slot, after the server has the aim it needs), so
 * it's safe to call from any tick phase. Reads return null/false/0 outside a world.
 */
interface InteractionHost {
    /** Null on a miss, an entity, or no world. */
    val crosshairBlock: BlockPos3?

    /** e.g. a player standing in front of the buttons. */
    val crosshairEntity: Boolean

    /** What a key click would hit, accounting for silent rotation. */
    val clickTargetBlock: BlockPos3? get() = crosshairBlock

    /**
     * What a targeted click sent now would hit, along the rotation the server already has. Null before the first
     * rotation, after this tick's movement went out, or when an entity is in the way.
     */
    val sentTargetBlock: BlockPos3? get() = null

    /** Accounting for silent rotation. */
    val clickTargetEntity: Boolean get() = crosshairEntity

    /** Null on a block, nothing, or no world. */
    val crosshairEntityId: UUID? get() = null

    /** Entity data as JSON plus a player's `Profile`. For dev tooling. */
    fun crosshairEntityRaw(): String? = null

    /** Accounting for silent rotation. */
    val clickTargetEntityId: UUID? get() = crosshairEntityId

    /**
     * Queues [action]. One pending request per [owner] - a new one cancels the old. Cancelled if the owner
     * disables; expires after a second. [onResult] runs once: on the client thread when sent/blocked/expired,
     * on the caller's thread when replaced/cancelled.
     */
    fun submit(
        action: ClientAction,
        owner: Any? = null,
        onResult: (ClientActionResult) -> Unit = {},
    ): ClickRequest

    /**
     * Counters Grim's DuplicateRotPlace: after a big server yaw change, nudges the aim slightly on-target
     * before the click and holds the hand-back for that tick. Off by default.
     */
    var settleBeforeClicks: Boolean
        get() = false
        set(_) {}

    fun cancel(owner: Any)

    fun rightClick(): ClickRequest = submit(ClientAction.Use)

    fun leftClick(): ClickRequest = submit(ClientAction.Attack)

    /** Holds [button] while any lease is held; once the last lets go it falls back to the physical key. Thread-safe. */
    fun hold(button: MouseButton, owner: Any): Lease

    /**
     * Presses keyboard key [name] (`key.keyboard.f5` or `f5`) until the lease is released; everything sees
     * it, our keybinds as a `synthetic` [org.blackaddons.api.event.input.KeyInputEvent]. Null if not a
     * keyboard key. Thread-safe.
     */
    fun pressKey(name: String): Lease? = null

    /** 0..8. */
    val selectedSlot: Int

    /** 0..8; out-of-range is ignored. */
    fun selectHotbarSlot(slot: Int)
}

enum class MouseButton {
    ATTACK,

    USE,
}

interface ClickRequest {
    /** Null while pending. */
    val result: ClientActionResult?

    val isPending: Boolean get() = result == null

    fun cancel()
}

enum class ClientActionResult {
    SENT,

    /** Something was in the way, or out of reach. */
    BLOCKED,

    /** No safe moment came (a screen stayed open, or the aim never reached the server). */
    EXPIRED,

    /** By [ClickRequest.cancel], a newer request from the owner, the owner disabling, or a world change. */
    CANCELLED,
}

sealed interface ClientAction {
    /** Whatever a key press would hit (crosshair, or the silent aim). */
    data object Use : ClientAction

    /** Whatever a key press would hit (crosshair, or the silent aim). */
    data object Attack : ClientAction

    /** Uses the held item without targeting anything (abilities, leaps). A use-key tap would hit what's in front. */
    data object UseItem : ClientAction

    /** Along the server rotation; BLOCKED if the ray hits something else first. */
    data class Interact(val entity: UUID) : ClientAction

    /**
     * Aims along the server rotation and is BLOCKED if the ray hits something else first. [onContact] also sends it
     * mid-turn, as soon as the rotation the server already has hits [block].
     */
    data class UseOn @JvmOverloads constructor(val block: BlockPos3, val onContact: Boolean = false) : ClientAction
}
