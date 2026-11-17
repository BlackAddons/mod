package org.blackaddons.core.features.impl.client

import org.blackaddons.api.Platform
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.feature.Categories
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.skyblock.LocationUtils
import org.blackaddons.api.skyblock.dungeon.Dungeon
import org.blackaddons.api.skyblock.dungeon.DungeonClass
import org.blackaddons.api.skyblock.dungeon.DungeonFloor
import org.blackaddons.api.util.I18n
import org.blackaddons.core.discord.DiscordActivity
import org.blackaddons.core.discord.DiscordRpc
import java.util.UUID

/**
 * Discord Rich Presence. It never shows the server address, and everything past "Multiplayer" is behind a privacy
 * toggle.
 */
object DiscordPresence : Feature(
    description = "Shows what you are doing in Minecraft on your Discord profile.",
    toggled = true,
) {
    override val section = Categories.Client.Menu

    private val tr = I18n.scope("blackaddons.discord_presence")

    private const val DOWNLOAD_URL = "https://blackaddons.com"
    private const val DISCORD_URL = "https://discord.gg/jt2tUZuhbe"
    private const val LOGO_URL = "https://cdn.blackaddons.com/static/logo/logo_512.png"
    private const val DUNGEON_PARTY = 5
    private const val REFRESH_TICKS = 20

    private val showLocation by boolean("Show Location", default = true)
        .withDescription("Hypixel, the SkyBlock area and the dungeon floor. Off = only singleplayer or multiplayer.")
    private val showParty by boolean("Show Party", default = true)
        .withDescription("Party size and your class in dungeons.")
    private val showTimer by boolean("Show Timer", default = true)
        .withDescription("Time since you entered the area or the dungeon run started.")
    private val showButtons by boolean("Show Buttons", default = true)
        .withDescription("Links to BlackAddons and its Discord on your profile. Only others see them, Discord hides them from you.")

    // New on every launch: Discord groups profiles by party id, so it can't stay the same long enough to link sessions.
    private val partyId = UUID.randomUUID().toString()

    private var ticks = 0
    private var place: Place? = null
    private var placeSince = System.currentTimeMillis()

    override fun init() {
        register<ClientTickEvent.Post> {
            if (ticks++ % REFRESH_TICKS == 0) refresh()
        }
    }

    override fun onEnable() {
        ticks = 0
        DiscordRpc.start()
    }

    override fun onDisable() = DiscordRpc.stop()

    private fun refresh() {
        val now = System.currentTimeMillis()
        val current = currentPlace()
        if (current.key != place?.key) placeSince = now
        place = current
        val runStart = (current as? Place.Dungeon)?.runStartedAt
        DiscordRpc.show(activity(current, startedAt = runStart ?: placeSince))
    }

    private fun currentPlace(): Place {
        val world = Platform.world
        return when {
            !world.isLoaded -> Place.MainMenu
            world.isSingleplayer -> Place.Singleplayer
            !showLocation.value || !LocationUtils.onHypixel -> Place.Multiplayer
            LocationUtils.inDungeon -> {
                val self = Dungeon.team.self
                Place.Dungeon(
                    floor = LocationUtils.dungeonFloor,
                    dungeonClass = self?.dungeonClass ?: DungeonClass.EMPTY,
                    classLevel = self?.classLevel ?: 0,
                    partySize = Dungeon.team.players.size,
                    runStartedAt = Dungeon.run.startTime?.real?.takeIf { Dungeon.run.started },
                )
            }
            LocationUtils.inSkyblock -> Place.Skyblock(LocationUtils.area)
            else -> Place.Hypixel
        }
    }

    private fun activity(place: Place, startedAt: Long): DiscordActivity {
        val (details, state) = when (place) {
            Place.MainMenu -> tr("main_menu", "In the main menu") to null
            Place.Singleplayer -> tr("singleplayer", "Singleplayer") to null
            Place.Multiplayer -> tr("multiplayer", "Multiplayer") to null
            Place.Hypixel -> tr("hypixel", "Hypixel") to null
            is Place.Skyblock -> tr("skyblock", "Hypixel SkyBlock") to place.area
            is Place.Dungeon -> dungeonText(place)
        }
        val party = (place as? Place.Dungeon)
            ?.takeIf { showParty.value && it.partySize > 0 }
            ?.let { DiscordActivity.Party(partyId, listOf(it.partySize, DUNGEON_PARTY)) }
        return DiscordActivity.of(
            details = details,
            state = state,
            startedAt = startedAt.takeIf { showTimer.value && place != Place.MainMenu },
            image = LOGO_URL,
            imageText = "BlackAddons ${Platform.blackaddonsVersion}",
            party = party,
            buttons = if (showButtons.value) buttons() else emptyList(),
        )
    }

    private fun buttons() = listOf(
        DiscordActivity.Button(tr("download", "Get BlackAddons"), DOWNLOAD_URL),
        DiscordActivity.Button(tr("discord", "Join the Discord"), DISCORD_URL),
    )

    private fun dungeonText(place: Place.Dungeon): Pair<String, String> {
        val floor = place.floor
        val details = when {
            floor == null -> tr("catacombs", "The Catacombs")
            floor == DungeonFloor.ENTRANCE -> tr("catacombs_entrance", "The Catacombs - Entrance")
            floor.isMasterMode -> tr("catacombs_master", "Master Mode - Floor %s", floor.number)
            else -> tr("catacombs_floor", "The Catacombs - Floor %s", floor.number)
        }
        val known = showParty.value && place.dungeonClass != DungeonClass.EMPTY
        val state = if (known) "${place.dungeonClass.displayName} ${place.classLevel}" else tr("in_run", "In a run")
        return details to state
    }

    private sealed interface Place {
        /** A change resets the timer. */
        val key: Any get() = this

        data object MainMenu : Place
        data object Singleplayer : Place
        data object Multiplayer : Place
        data object Hypixel : Place

        data class Skyblock(val area: String?) : Place

        data class Dungeon(
            val floor: DungeonFloor?,
            val dungeonClass: DungeonClass,
            val classLevel: Int,
            val partySize: Int,
            val runStartedAt: Long?,
        ) : Place {
            override val key: Any get() = floor ?: "dungeon"
        }
    }
}
