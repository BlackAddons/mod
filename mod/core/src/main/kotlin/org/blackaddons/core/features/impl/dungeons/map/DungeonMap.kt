package org.blackaddons.core.features.impl.dungeons.map

import org.blackaddons.api.Platform
import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.event.dungeon.DungeonDoorOpenedEvent
import org.blackaddons.api.event.subscribe
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.skyblock.dungeon.DungeonClass
import org.blackaddons.api.ui.toast.ToastKind
import org.blackaddons.api.ui.toast.Toasts
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.concurrent.OffThread
import org.blackaddons.api.util.equalsIgnoreCase
import org.blackaddons.api.util.io.FileFormat
import org.blackaddons.core.dungeon.map.*
import org.blackaddons.core.log.Log
import java.awt.Color
import java.nio.file.Path

object DungeonMap : Feature("Renders a live dungeon map HUD during Catacombs runs.") {

    const val CALCULATION_OPTIMISTIC = "Optimistic"
    const val CALCULATION_CONSERVATIVE = "Conservative"

    private val logger = Log.area("dungeon-map")

    val hideInBoss by boolean("Hide In Boss", true)
        .withDescription("Hide the map overlay during boss fights.")
        .group("General")
    val bossPeekKey by keybind("Boss Peek Keybind")
        .withDescription("Hold to show the map during boss fights.")
        .showIf { hideInBoss.value }
        .group("General")
    val playerNames by mode("Player Names", "Off", listOf("Off", "Holding Leap", "Always"))
        .withDescription("When to show player names on the map.")
        .group("General")
    val customMarker by boolean("Custom Marker", false)
        .withDescription("Use a custom directional map marker for yourself instead of a player head.")
        .group("General")
    val roomPrediction by boolean("Room Prediction", true)
        .withDescription("Predict unopened 1x1 room types while using legit map mode.")
        .group("General")
    val calculationMode by mode(
        "Calculation Method", CALCULATION_OPTIMISTIC,
        listOf(CALCULATION_OPTIMISTIC, CALCULATION_CONSERVATIVE),
    ).withDescription(
        "Optimistic: live score assumes perfect Speed; its S+ secret goal assumes unfinished puzzles will be completed and uses the projection options below.\n" +
            "Conservative: live score uses elapsed-time Speed; its S+ secret goal keeps unfinished-puzzle penalties and only current detected bonuses.\n" +
            "Both include death penalties."
    )
        .group("General")
    val maxBonusForSecretGoal by boolean("Max Bonus For Secret Goal", true)
        .withDescription("Use the configurable bonus assumptions below when calculating the S+ secret goal.")
        .showIf { calculationMode.value == CALCULATION_OPTIMISTIC }
        .group("General")
    val dynamicCryptsForSecretGoal by boolean("Use Current Crypts", false)
        .withDescription("Use the current Crypt count instead of assuming all 5 Crypt bonus points.")
        .showIf { calculationMode.value == CALCULATION_OPTIMISTIC && maxBonusForSecretGoal.value }
        .group("General")
    val assumeMimicForSecretGoal by boolean("Assume Mimic Bonus", true)
        .withDescription("Assume the +2 Mimic bonus will be earned on Floor VI and above.")
        .showIf { calculationMode.value == CALCULATION_OPTIMISTIC && maxBonusForSecretGoal.value }
        .group("General")
    val assumePrinceForSecretGoal by boolean("Assume Prince Bonus", true)
        .withDescription("Assume the +1 Prince bonus will be earned while a Prince room remains possible.")
        .showIf { calculationMode.value == CALCULATION_OPTIMISTIC && maxBonusForSecretGoal.value }
        .group("General")
    val assumeBatForSecretGoal by boolean("Assume Bat Bonus", false)
        .withDescription("Assume the +1 Bat bonus will be earned before it is detected.")
        .showIf { calculationMode.value == CALCULATION_OPTIMISTIC && maxBonusForSecretGoal.value }
        .group("General")
    val showNeededSecrets by boolean("Show Needed Secrets", false)
        .withDescription("Show the total secrets needed for S+ instead of the remaining amount.")
        .showIf { calculationMode.value == CALCULATION_OPTIMISTIC }
        .group("General")
    val includePaulInSecretGoal by boolean("Include Paul In Secret Goal", false)
        .withDescription("Assume Paul's +10 score bonus when calculating the S+ secret goal.")
        .showIf { calculationMode.value == CALCULATION_OPTIMISTIC }
        .group("General")

    val showExtraInfo by boolean("Extra Info", true)
        .withDescription("Show additional info below the map.")
        .group("Extra Info")
    val extraInfoSecrets by boolean("Show Secrets", true)
        .withDescription("Show secrets found / total count.")
        .showIf { showExtraInfo.value }
        .group("Extra Info")
    val extraInfoCrypts by boolean("Show Crypts", true)
        .withDescription("Show crypts count.")
        .showIf { showExtraInfo.value }
        .group("Extra Info")
    val extraInfoScore by boolean("Show Score", true)
        .withDescription("Show calculated dungeon score.")
        .showIf { showExtraInfo.value }
        .group("Extra Info")
    val extraInfoDeaths by boolean("Show Deaths", true)
        .withDescription("Show team death count.")
        .showIf { showExtraInfo.value }
        .group("Extra Info")
    val extraInfoBonusStyle by mode("Bonus Style", "Custom Icons", listOf("Custom Icons", "Letters", "Off"))
        .withDescription("Style for Mimic, Prince, and Bat bonus indicators.")
        .showIf { showExtraInfo.value }
        .group("Extra Info")
    val forcePaul by boolean("Force Paul", false)
        .withDescription("Always add +10 bonus score for Mayor Paul's EZPZ perk.")
        .showIf { showExtraInfo.value }
        .group("Extra Info")
    val forceDisablePaul by boolean("Force Disable Paul", false)
        .withDescription("Never add Mayor Paul's +10 EZPZ score bonus, even when it is detected.")
        .showIf { showExtraInfo.value }
        .group("Extra Info")

    val textScale by number("Text Scale", 1.0, 0.4, 1.5, 0.1)
        .withDescription("Scale for room name / secret text on the map.")
        .group("Scales")
    val checkmarkScale by number("Checkmark Scale", 1.0, 0.3, 1.5, 0.1)
        .withDescription("Scale for checkmark icons on the map.")
        .group("Scales")
    val playerHeadScale by number("Player Head Scale", 1.0, 0.3, 1.5, 0.1)
        .withDescription("Scale for player head icons.")
        .group("Scales")
    val playerNameScale by number("Player Name Scale", 1.0, 0.3, 1.5, 0.1)
        .withDescription("Scale for player name labels.")
        .group("Scales")

    val antiAliasRooms by boolean("Antialias Rooms", true)
        .withDescription("Smooth room edges.")
        .group("Background & Border")
    val roomCornerRadius by number("Corner Radius", 3.0, 0.0, 6.0, 0.5, unit = "px")
        .withDescription("Corner rounding radius for the map frame.")
        .group("Background & Border")
    val doorWidth by number("Door Width", 6.0, 2.0, 12.0, 1.0, unit = "px")
        .withDescription("How wide doors between rooms are drawn on the map.")
        .group("Background & Border")
    val mapBackground by color("Background", Color(255, 255, 255, 50), allowAlpha = true)
        .withDescription("Map background color.")
        .group("Background & Border")
    val customBackground by boolean("Custom Background", false, id = "custom_background_enabled")
        .withDescription("Use a PNG, JPEG, WebP, or GIF behind the map. Choose the file below.")
        .group("Background & Border")
    private val customBackgroundFile by string("Custom Background File", "", maxLength = 512, id = "custom_background_file")
        .hidden()
    val chooseCustomBackground by button(
        name = "Background File",
        label = "Choose...",
        id = "choose_custom_background",
        action = ::openCustomBackgroundPicker,
    ).withDescription("Choose the PNG, JPEG, WebP, or GIF to draw behind the map.")
        .showIf { customBackground.value }
        .group("Background & Border")
    val backgroundDarkness by number("Background Darkness", 0.0, 0.0, 100.0, 1.0, unit = "%")
        .withDescription("Darken the map background without darkening rooms, labels, or player icons.")
        .group("Background & Border")
    val backgroundTransparency by number("Background Transparency", 0.0, 0.0, 100.0, 1.0, unit = "%")
        .withDescription("Make the entire map background, including its darkness, transparent. 100% hides it completely.")
        .group("Background & Border")
    val mapBorderColor by color("Border Color", Color.WHITE, allowAlpha = true)
        .withDescription("Map border color.")
        .group("Background & Border")
    val borderWidth by number("Border Width", 1.0, 1.0, 5.0, 1.0, unit = "px")
        .withDescription("Map border thickness.")
        .group("Background & Border")

    val checkmarkStyle by mode(
        "Checkmark Style", "Room Name + Secrets",
        listOf("Checkmarks", "Secrets", "Room Name", "Room Name + Secrets"),
    ).withDescription("What to show over cleared rooms.")
        .group("Checkmarks")
    val centerCheckmark by boolean("Center Checkmark", true)
        .withDescription("Center checkmarks on multi-tile rooms instead of top-left.")
        .group("Checkmarks")
    val hideUnknownCheckmark by boolean("Hide Unknown Checkmark", true)
        .withDescription("Hide the question mark on unopened rooms.")
        .group("Checkmarks")
    val princeRoomIcon by boolean("Prince Room Icon", false)
        .withDescription("Show a crown on rooms that can contain Prince until he is killed.")
        .group("Checkmarks")
    val limitRoomNameSize by boolean("Limit Room Name Size", true)
        .withDescription("Shrink room names to fit within the room's bounds.")
        .showIf { checkmarkStyle.value == "Room Name" || checkmarkStyle.value == "Room Name + Secrets" }
        .group("Checkmarks")

    val headBorderColor by color("Head Border", Color.BLACK, allowAlpha = true)
        .withDescription("Default border color for player heads.")
        .group("Player Heads")
    val headDirectionIndicator by boolean("Head Direction Indicator", false)
        .withDescription("Show a direction indicator on rendered player heads.")
        .group("Player Heads")
    val markerColor by color("Marker Color", Color(0, 255, 0), allowAlpha = true)
        .withDescription("Custom marker tint color.")
        .group("Player Heads")
    val headBorderClassBased by boolean("Class-Based Borders", false)
        .withDescription("Color head borders by dungeon class.")
        .group("Player Heads")
    val playerNameClassBased by boolean("Class-Based Names", false)
        .withDescription("Color player names by dungeon class.")
        .group("Player Heads")

    private val colorArcher by color("Archer", DungeonClass.ARCHER.color, allowAlpha = true)
        .showIf(::usesClassColors)
        .group("Class Colors")
    private val colorBerserk by color("Berserk", DungeonClass.BERSERK.color, allowAlpha = true)
        .showIf(::usesClassColors)
        .group("Class Colors")
    private val colorHealer by color("Healer", DungeonClass.HEALER.color, allowAlpha = true)
        .showIf(::usesClassColors)
        .group("Class Colors")
    private val colorMage by color("Mage", DungeonClass.MAGE.color, allowAlpha = true)
        .showIf(::usesClassColors)
        .group("Class Colors")
    private val colorTank by color("Tank", DungeonClass.TANK.color, allowAlpha = true)
        .showIf(::usesClassColors)
        .group("Class Colors")

    private fun usesClassColors() = headBorderClassBased.value || playerNameClassBased.value

    fun classColor(dungeonClass: DungeonClass?): Color = when (dungeonClass) {
        DungeonClass.ARCHER -> colorArcher.value
        DungeonClass.BERSERK -> colorBerserk.value
        DungeonClass.HEALER -> colorHealer.value
        DungeonClass.MAGE -> colorMage.value
        DungeonClass.TANK -> colorTank.value
        DungeonClass.EMPTY, null -> Color.WHITE
    }

    val colorUnopened by color("Unopened Room", Color(65, 65, 65), allowAlpha = true)
        .group("Room Colors")
    val colorBlood by color("Blood Room", Color(178, 0, 0), allowAlpha = true)
        .group("Room Colors")
    val colorFairy by color("Fairy Room", Color(227, 155, 226), allowAlpha = true)
        .group("Room Colors")
    val colorRare by color("Rare Room", Color(178, 178, 178), allowAlpha = true)
        .group("Room Colors")
    val colorMiniboss by color("Miniboss Room", Color(255, 200, 0), allowAlpha = true)
        .group("Room Colors")
    val colorPuzzle by color("Puzzle Room", Color(123, 0, 123), allowAlpha = true)
        .group("Room Colors")
    val colorTrap by color("Trap Room", Color(255, 130, 0), allowAlpha = true)
        .group("Room Colors")
    val colorRoom by color("Normal Room", Color(121, 70, 0), allowAlpha = true)
        .group("Room Colors")
    val colorEntrance by color("Entrance Room", Color(0, 255, 0), allowAlpha = true)
        .group("Room Colors")

    val colorUnopenedDoor by color("Unopened Door", Color(65, 65, 65), allowAlpha = true)
        .group("Door Colors")
    val colorBloodDoor by color("Blood Door", Color(178, 0, 0), allowAlpha = true)
        .group("Door Colors")
    val colorWitherDoor by color("Wither Door", Color(16, 16, 16), allowAlpha = true)
        .group("Door Colors")
    val colorRoomDoor by color("Normal Door", Color(121, 70, 0), allowAlpha = true)
        .group("Door Colors")
    val colorOpenWitherDoor by color("Open Wither Door", Color(121, 70, 0), allowAlpha = true)
        .group("Door Colors")
    val colorEntranceDoor by color("Entrance Door", Color(0, 255, 0), allowAlpha = true)
        .group("Door Colors")

    val debugLog by boolean("Debug Logging", false)
        .withDescription("Print map packet, calibration, and room scan debug messages to the console.")
        .group("Debug")
    val collectUnknownRoomData by boolean("Collect Unknown Room Data", true)
        .withDescription("Record unknown room hashes for future room catalog updates. Disable to stop collecting them.")
        .group("Debug")
    val collectUnknownRoomSnapshots by boolean("Collect Unknown Room Snapshots", true)
        .withDescription("Save a schematic snapshot when an unknown room is found. These files can be large.")
        .showIf { collectUnknownRoomData.value }
        .group("Debug")

    fun debug(msg: () -> String) {
        if (debugLog.value) {
            logger.info(msg())
        }
    }

    internal fun isCustomBackgroundFile(value: String): Boolean {
        if (value.isBlank()) return true
        return runCatching { FileFormat.isImageFile(Path.of(value.trim())) }.getOrDefault(false)
    }

    internal fun selectedCustomBackgroundFile(): String? =
        customBackgroundFile.value.trim().takeIf { customBackground.value && it.isNotEmpty() }

    private fun openCustomBackgroundPicker() {
        Platform.desktop.chooseFile("Choose Dungeon Map Background", FileFormat.IMAGE_EXTENSIONS) { path ->
            OffThread.run(
                name = "map-background-check",
                work = { isCustomBackgroundFile(path.toString()) },
                onFailure = { false },
                onResult = { valid -> Platform.scheduler.schedule(0) { finishBackgroundSelection(path, valid) } },
            )
        }
    }

    private fun finishBackgroundSelection(path: Path, valid: Boolean) {
        if (!valid) {
            Toasts.show(
                I18n.trOr("blackaddons.dungeon_map.background_invalid", "Unsupported map background"),
                I18n.trOr("blackaddons.dungeon_map.background_invalid_detail", "Use PNG/JPEG/WebP/GIF."),
                ToastKind.ERROR,
            )
            return
        }
        customBackgroundFile.value = path.toString()
        customBackground.value = true
        Platform.config.save()
    }

    override fun init() {
        register<WorldChangeEvent> { EnteredRoomReveal.reset() }
        register<ClientTickEvent.Post> { EnteredRoomReveal.tick() }
        RoomHashCollection.init()
        DungeonTree.init()
        MapRenderer.register(this)

        EventBus.subscribe<DungeonDoorOpenedEvent> {
            val candidates = MapState.dungeonList
                .filterIsInstance<DoorTile>()
                .filter { !it.opened && it.type == event.type }
            val opener = event.player
            val selfName = Platform.player.name
            val nearest = if (opener == null || opener.equalsIgnoreCase(selfName)) {
                val px = Platform.player.x
                val pz = Platform.player.z
                candidates.minByOrNull { (it.x - px) * (it.x - px) + (it.z - pz) * (it.z - pz) }
            } else {
                val marker = MapState.player(opener)?.takeIf { it.icon != null } ?: return@subscribe
                candidates.minByOrNull {
                    val (mapX, mapZ) = MapState.worldToMap(it.x.toDouble(), it.z.toDouble())
                    val dx = mapX - marker.targetX
                    val dz = mapZ - marker.targetZ
                    dx * dx + dz * dz
                }
            }
            if (nearest != null) {
                nearest.opened = true
                debug { "[DungeonMap] Marked door opened from event: type=${event.type}, door=(${nearest.x}, ${nearest.z})" }
            }
        }
    }
}
