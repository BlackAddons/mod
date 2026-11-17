package org.blackaddons.api.skyblock.player

import org.blackaddons.api.HostApi
import org.blackaddons.api.skyblock.SkyblockItem
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Saves the player from a lethal hit. [mask] is null for the pet, and [proc] matches the unformatted chat line. */
enum class InvincibilityItem(
    val displayName: String,
    val cooldown: Duration,
    private val proc: Regex,
    val mask: SkyblockItem? = null,
) {
    SPIRIT(
        "Spirit Mask", 30.seconds, Regex.fromLiteral("Second Wind Activated! Your Spirit Mask saved your life!"),
        SkyblockItem("SPIRIT_MASK", "Spirit Mask"),
    ),
    BONZO(
        "Bonzo's Mask", 180.seconds, Regex("""^Your (?:\S+ )?Bonzo's Mask saved your life!$"""),
        SkyblockItem(setOf("BONZO_MASK", "STARRED_BONZO_MASK"), listOf("Bonzo's Mask")),
    ),
    PHOENIX("Phoenix", 60.seconds, Regex.fromLiteral("Your Phoenix Pet saved you from certain death!")),
    ;

    companion object {
        fun fromMessage(unformatted: String): InvincibilityItem? = entries.firstOrNull { it.proc.matches(unformatted) }
    }
}

/** Invincibility cooldowns in server ticks, counted from the proc lines. Read them on the client thread. */
object Invincibility {

    @Volatile
    private var bound: InvincibilityProvider? = null

    private val provider: InvincibilityProvider
        get() = bound ?: error("Invincibility was read before the host bound its provider")

    /** Host only. */
    @HostApi
    fun bind(provider: InvincibilityProvider) {
        synchronized(this) {
            bound?.let { error("Invincibility is already bound to ${it::class.java.name}") }
            bound = provider
        }
    }

    fun cooldownTicks(item: InvincibilityItem): Int = provider.cooldownTicks(item)

    fun isReady(item: InvincibilityItem): Boolean = cooldownTicks(item) <= 0

    /** The mask in the helmet slot, read each time it is called. */
    val wornMask: InvincibilityItem? get() = provider.wornMask
}

/** Host side of [Invincibility], bound once. */
interface InvincibilityProvider {
    fun cooldownTicks(item: InvincibilityItem): Int
    val wornMask: InvincibilityItem?
}
