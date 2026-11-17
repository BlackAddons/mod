package org.blackaddons.core.features.impl.dungeons.leapmenu

import org.blackaddons.api.Platform
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.event.chat.ChatReceiveEvent
import org.blackaddons.api.event.dungeon.SpiritLeapEvent
import org.blackaddons.api.event.render.PlayerVisibilityEvent
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.input.Keys
import org.blackaddons.api.skyblock.F7SubLocation
import org.blackaddons.api.skyblock.LocationUtils
import org.blackaddons.api.skyblock.dungeon.Dungeon
import org.blackaddons.api.skyblock.dungeon.DungeonClass
import org.blackaddons.api.skyblock.dungeon.SpiritLeapMenu
import org.blackaddons.api.world.Vec3d

object LeapMenu : Feature(
    description = "Replaces the Spirit Leap menu with teammate cards showing their view and location, with keybinds and map leaping.",
    name = "Leap Menu",
) {
    const val LABEL_CLASS = "Class"
    const val LABEL_NAME = "Name"
    const val LABEL_BOTH = "Name + Class"
    const val KEYS_OFF = "Off"
    const val KEYS_CORNERS = "Corners"
    const val KEYS_CLASS = "Class"

    val sorting by mode("Sorting", LeapOrder.CLASS, LeapOrder.MODES)
        .withDescription("Which teammate goes in which corner, filled left to right, top to bottom.")
        .group("General")
    val customOrder by list(
        "Custom Order",
        default = DungeonClass.entries.filter { it != DungeonClass.EMPTY }.map { it.displayName },
        placeholder = "Player or class",
        itemMaxLength = 16,
    ).withDescription("Player names or classes, top first. A teammate takes the place of their first match.")
        .showIf { sorting.value == LeapOrder.CUSTOM }
        .group("General")
    val label by mode("Label", LABEL_BOTH, listOf(LABEL_CLASS, LABEL_NAME, LABEL_BOTH))
        .withDescription("What each card names: the class, the player, or both.")
        .group("General")
    val showHead by boolean("Show Head", default = true)
        .withDescription("Draw the teammate's head on their card.")
        .group("General")
    val showView by boolean("Teammate View", default = true)
        .withDescription("Show what each teammate sees on their card. Hidden while a shader pack is in use.")
        .group("General")
    val viewFps by number("View FPS", default = 60.0, min = 5.0, max = 120.0, step = 5.0, unit = "fps")
        .withDescription("How often the hovered card's view is redrawn. The others redraw a few times a second; every redraw renders the world once more.")
        .showIf { showView.value }
        .group("General")
    val showLocation by boolean("Show Location", default = true)
        .withDescription("The room each teammate is in; in the boss, the boss phase, with the section in Floor 7 P3.")
        .group("General")
    val scale by number("Scale", default = 2.0, min = 0.5, max = 2.0, step = 0.05, unit = "x")
        .withDescription("Card size. Cards shrink further when they would not fit the screen.")
        .group("General")
    val classColored by boolean("Class Colored Cards", default = false)
        .withDescription("Tint cards with the class color.")
        .group("General")
    val tintDead by boolean("Tint Dead Players", default = true)
        .withDescription("Tint the cards of dead teammates red.")
        .group("General")
    val showDoorOpener by boolean("Show Last Door Opener", default = false)
        .withDescription("Outline the teammate who opened the last Wither door.")
        .group("General")
    val leftClickOnly by boolean("Left Click Only", default = false)
        .withDescription("Leap only on a left click.")
        .group("General")
    val onRelease by boolean("Leap On Release", default = false)
        .withDescription("Leap when the mouse button is released instead of pressed.")
        .group("General")

    val keybindMode by mode("Keybind Mode", KEYS_CORNERS, listOf(KEYS_OFF, KEYS_CORNERS, KEYS_CLASS))
        .withDescription("Corners: one key per corner.\nClass: one key per class.")
        .group("Keybinds")
    val topLeftKey by keybind("Top Left", Keys.DIGIT_1)
        .showIf { keybindMode.value == KEYS_CORNERS }
        .group("Keybinds")
    val topRightKey by keybind("Top Right", Keys.DIGIT_2)
        .showIf { keybindMode.value == KEYS_CORNERS }
        .group("Keybinds")
    val bottomLeftKey by keybind("Bottom Left", Keys.DIGIT_3)
        .showIf { keybindMode.value == KEYS_CORNERS }
        .group("Keybinds")
    val bottomRightKey by keybind("Bottom Right", Keys.DIGIT_4)
        .showIf { keybindMode.value == KEYS_CORNERS }
        .group("Keybinds")
    val archerKey by keybind("Archer", Keys.DIGIT_1)
        .showIf { keybindMode.value == KEYS_CLASS }
        .group("Keybinds")
    val berserkKey by keybind("Berserk", Keys.DIGIT_2)
        .showIf { keybindMode.value == KEYS_CLASS }
        .group("Keybinds")
    val healerKey by keybind("Healer", Keys.DIGIT_3)
        .showIf { keybindMode.value == KEYS_CLASS }
        .group("Keybinds")
    val mageKey by keybind("Mage", Keys.DIGIT_4)
        .showIf { keybindMode.value == KEYS_CLASS }
        .group("Keybinds")
    val tankKey by keybind("Tank", Keys.DIGIT_5)
        .showIf { keybindMode.value == KEYS_CLASS }
        .group("Keybinds")

    val mapLeap by boolean("Map Leap", default = false)
        .withDescription("During clear, click a teammate on the dungeon map instead of a card.")
        .group("Map Leap")
    val mapLeapAfterBlood by boolean("After Blood Only", default = false)
        .withDescription("Keep the cards until the Blood Room is opened.")
        .showIf { mapLeap.value }
        .group("Map Leap")
    val mapLeapScale by number("Map Scale", default = 3.0, min = 0.5, max = 5.0, step = 0.1, unit = "x")
        .showIf { mapLeap.value }
        .group("Map Leap")

    val announce by boolean("Leap Announce", default = false)
        .withDescription("Tell your party who you leaped to.")
        .group("After Leap")
    val announceMessage by string("Leap Message", default = "Leaped to {name}!", placeholder = "Leaped to {name}!", maxLength = 100)
        .withDescription("{name} becomes the teammate's name.")
        .showIf { announce.value }
        .group("After Leap")
    val hidePlayers by boolean("Hide Players", default = false)
        .withDescription("Hide teammates standing on you for a moment after you leap.")
        .group("After Leap")
    val hideTime by number("Hide Time", default = 3.5, min = 0.5, max = 5.0, step = 0.1, unit = "s")
        .showIf { hidePlayers.value }
        .group("After Leap")

    /* Set while a leap clicks its own head. The overlay then names the teammate and shows no cards. */
    var leapingTo: String? = null
        private set

    fun teammateAt(spot: F7SubLocation): String? = TeammateLocation.at(spot)

    internal val cornerKeys = listOf(topLeftKey, topRightKey, bottomLeftKey, bottomRightKey)
    internal val classKeys = mapOf(
        DungeonClass.ARCHER to archerKey,
        DungeonClass.BERSERK to berserkKey,
        DungeonClass.HEALER to healerKey,
        DungeonClass.MAGE to mageKey,
        DungeonClass.TANK to tankKey,
    )

    private const val HIDE_RADIUS_SQ = 4.0
    private const val MS_PER_SECOND = 1000.0

    private var hideUntil = 0L

    override fun init() {
        register<SpiritLeapEvent.Started> { leapingTo = event.target }
        register<SpiritLeapEvent.Ended> { leapingTo = null }
        Platform.containerOverlays.register(LeapMenuOverlay)
        register<ClientTickEvent.Post> {
            TeammateLocation.track()
            LeapMenuOverlay.tick()
        }
        register<WorldChangeEvent> {
            LeapMenuOverlay.reset()
            TeammateLocation.reset()
            hideUntil = 0L
        }
        register<ChatReceiveEvent> {
            if (!LocationUtils.inDungeon) return@register
            TeammateLocation.onChat(event.unformatted)
            val name = SpiritLeapMenu.leapedTo(event.unformatted) ?: return@register
            if (announce.value) {
                announceMessage.value.replace("{name}", name).takeIf { it.isNotBlank() }?.let { Platform.chat.sendCommand("pc $it") }
            }
            if (hidePlayers.value) hideUntil = System.currentTimeMillis() + (hideTime.value * MS_PER_SECOND).toLong()
        }
        register<PlayerVisibilityEvent> {
            if (System.currentTimeMillis() > hideUntil) return@register
            if (Platform.player.pos.distanceSqr(Vec3d(event.x, event.y, event.z)) > HIDE_RADIUS_SQ) return@register
            if (Dungeon.team.player(event.name) != null) event.cancelled = true
        }
    }

    override fun onDisable() {
        super.onDisable()
        LeapMenuOverlay.reset()
        hideUntil = 0L
    }
}
