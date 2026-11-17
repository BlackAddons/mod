package org.blackaddons.core.features.impl.client

import org.blackaddons.api.Platform
import org.blackaddons.api.event.network.ClientBrandEvent
import org.blackaddons.api.event.network.CustomPayloadSendEvent
import org.blackaddons.api.event.network.KnownPackEvent
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.feature.translatedName
import org.blackaddons.api.ui.screen.UiManager
import org.blackaddons.api.util.dev.DevFileLogger
import org.blackaddons.api.util.equalsIgnoreCase
import org.blackaddons.api.util.startsWithIgnoreCase
import org.blackaddons.core.features.impl.client.ModlistHider.HYPIXEL_CHANNELS
import org.blackaddons.core.features.impl.client.ModlistHider.chooseMods
import org.blackaddons.core.ui.screen.ModPickerScreen

/**
 * Hides installed mods from the server. All disclosure points are events.
 * - Vanilla: drops every C2S custom payload, channel registration included, hides fabric packs, brand `"vanilla"`. The
 *   brand payload still goes out, because vanilla always sends one.
 * - Hypixel: like Vanilla, but [HYPIXEL_CHANNELS] pass and are the only ones announced (the Mod API isn't a
 *   fingerprint).
 * - Custom: allowed channels/mods and a custom brand. "Disable Custom Payloads" also filters the announcement.
 * No "Modded" mode: that's just this feature off (Fabric already reports `"fabric"`).
 */
object ModlistHider : Feature(
    description = "Hide your installed mods from the server.",
) {

    enum class Mode { VANILLA, HYPIXEL, CUSTOM }

    /** `hyevent:` carries the location packet other mods wait for. */
    private val HYPIXEL_CHANNELS = listOf("hypixel:", "hyevent:")

    private val modeSetting by mode(
        name = "Mode",
        default = "Hypixel",
        modes = listOf("Vanilla", "Hypixel", "Custom"),
    ).withDescription("Hypixel = hide everything except Hypixel's own Mod API.\nVanilla = hide that too.\nCustom = reveal only what you allow below.")
        .unsafeWhen(
            "BREAKS MODS",
            "Vanilla drops Hypixel's Mod API handshake along with everything else, so the server " +
                "never tells the client which island it is on. Every mod built on that — SkyHanni, " +
                "Nebula, ours — goes quiet without an error. Use Hypixel unless you need total silence",
        ) { it == "Vanilla" }

    private val disableCustomPayloads by boolean(
        name = "Disable Custom Payloads",
        default = false,
    ).withDescription("Strip channels missing from the allowlist out of register/unregister packets.")
        .showIf { mode == Mode.CUSTOM }

    private val allowedChannelsSetting by list(
        name = "Allowed Channels",
        default = listOf(
            "fabric:attachment_sync_v1",
            "fabric:recipe_sync",
            "fabric-screen-handler-api-v1:open_screen",
            "hypixel:ping",
            "hypixel:party_info",
            "hypixel:player_info",
            "hypixel:hello",
            "hypixel:register",
            "hyevent:location",
        ),
        placeholder = "e.g. minecraft:brand",
    ).withDescription("Channels that survive payload filtering in Custom mode.")
        .showIf { mode == Mode.CUSTOM }

    /**
     * Hidden list behind [chooseMods]. Ids don't have to be loaded mods, prefixes still match packs. Defaults to loader
     * builtins. `fabric` isn't in it, because prefix matching would let every fabric pack back in.
     */
    private val allowedModsSetting by list(
        name = "Allowed Mods",
        default = listOf("minecraft", "fabricloader", "java"),
        placeholder = "e.g. fabric",
    ).hidden()

    // Fixed `id`, because the default (the name) would collide with allowedModsSetting.
    private val chooseMods by button(
        name = "Allowed Mods",
        action = { UiManager.push(ModPickerScreen(allowedModsSetting, title = allowedModsSetting.translatedName)) },
        label = "Open",
        id = "choose_mods",
    ).withDescription("Tick the mods the server may see, chosen from the ones you actually have installed.")
        .showIf { mode == Mode.CUSTOM }

    private val customBrand by string(
        name = "Custom Brand",
        default = "fabric",
        placeholder = "fabric",
        maxLength = 32,
    ).withDescription("Brand string sent to the server in Custom mode.")
        .showIf { mode == Mode.CUSTOM }

    private val mode: Mode
        get() = when (modeSetting.value) {
            "Custom" -> Mode.CUSTOM
            "Hypixel" -> Mode.HYPIXEL
            else -> Mode.VANILLA
        }

    private fun allowed(channel: String): Boolean {
        val prefixes = when (mode) {
            Mode.VANILLA -> return false
            Mode.HYPIXEL -> HYPIXEL_CHANNELS
            Mode.CUSTOM -> allowedChannelsSetting.value
        }
        return prefixes.any { channel.startsWithIgnoreCase(it) }
    }

    /** Not in singleplayer: spoofing drops registry sync and KnownPacks replies, and "Preparing spawn area" deadlocks. */
    private val shouldSpoof: Boolean
        get() = enabled && !Platform.world.isSingleplayer

    private val guardTextComponents by boolean(
        name = "Sign & Anvil Guard",
        default = true,
    ).withDescription("Prevent servers from discovering installed mods via manipulated translation components in signs or anvils.")

    val shouldSanitizeComponents: Boolean
        get() = shouldSpoof && guardTextComponents.value

    val allowedMods: List<String>
        get() = if (mode == Mode.CUSTOM) allowedModsSetting.value else listOf("minecraft", "fabricloader", "java")

    private val devLog = DevFileLogger.named("modlist-hider")

    fun onBlockedProbe(key: String, source: String) {
        log("BLOCK_PROBE", key, "source=$source")
    }

    override fun init() {
        register<ClientBrandEvent> {
            if (!shouldSpoof) return@register
            event.brand = when (mode) {
                Mode.VANILLA, Mode.HYPIXEL -> event.vanillaBrand
                Mode.CUSTOM -> customBrand.value.takeIf { it.isNotEmpty() }
            }
        }

        register<KnownPackEvent> {
            if (!shouldSpoof) return@register
            // Only fabric packs fingerprint the loader. Hiding other packs costs a full resend.
            if (!event.namespace.equalsIgnoreCase("fabric")) return@register
            event.hidden = when (mode) {
                // Hypixel's API uses payloads, not packs.
                Mode.VANILLA, Mode.HYPIXEL -> true
                Mode.CUSTOM -> allowedModsSetting.value.none { event.id.startsWithIgnoreCase(it) }
            }
        }

        register<CustomPayloadSendEvent> {
            if (!shouldSpoof) return@register
            val channel = event.channel

            if (event.isChannelRegistration) {
                when {
                    // The most revealing packet. Dropping it looks clean, because Fabric skips it when there are no mod channels.
                    mode == Mode.VANILLA -> {
                        event.cancelled = true
                        log("CANCEL", channel, "channel registration")
                    }
                    // Hypixel only sends on a channel if it was announced. Filter the real list, never invent ids.
                    mode == Mode.HYPIXEL || disableCustomPayloads.value -> {
                        val announced = event.registeredChannels
                        if (announced.isEmpty()) {
                            // Unreadable: an empty list drops every channel, passing it on only loses the secret.
                            log("PASS", channel, "channel registration, channels unreadable")
                        } else {
                            val kept = announced.filter(::allowed)
                            /*
                             * Send nothing, not an empty list, because a clean client never sends an empty list. This
                             * happens in Hypixel mode when an API hooks the codec (hm-api) instead of registering
                             * receivers.
                             */
                            if (kept.isEmpty()) {
                                event.cancelled = true
                                log("CANCEL", channel, "channel registration, nothing allowed")
                            } else {
                                event.announceChannels = kept
                                log("REWRITE", channel, "kept=${kept.size}/${announced.size}")
                            }
                        }
                    }
                    else -> log("PASS", channel, "channel registration, rewrite disabled")
                }
                return@register
            }

            if (allowed(channel)) {
                log("PASS", channel, "matched allowlist")
            } else {
                event.cancelled = true
                log("CANCEL", channel, "not in allowlist")
            }
        }
    }

    private fun log(decision: String, channelId: String, extra: String? = null) {
        devLog.write(buildString {
            append("decision=").append(decision)
            append("  channel=").append(channelId)
            append("  mode=").append(mode.name)
            if (extra != null) append("  ").append(extra)
        })
    }
}
