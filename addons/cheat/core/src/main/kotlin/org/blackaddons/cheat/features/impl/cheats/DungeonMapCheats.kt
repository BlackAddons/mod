package org.blackaddons.cheat.features.impl.cheats

import org.blackaddons.api.Platform
import org.blackaddons.api.event.render.RenderWorldEvent
import org.blackaddons.api.feature.FeatureExtension
import org.blackaddons.api.skyblock.LocationUtils
import org.blackaddons.api.skyblock.dungeon.Dungeon
import org.blackaddons.api.skyblock.dungeon.MapOverride
import org.blackaddons.api.skyblock.dungeon.MapOverrides
import org.blackaddons.core.features.impl.dungeons.map.DungeonMap
import java.awt.Color

/** Cheater Map and Mimic ESP inside [DungeonMap]. */
object DungeonMapCheats : FeatureExtension(DungeonMap, group = "Cheats"), MapOverride {
    internal const val INSTANT_ROOM_UPDATE_ALL_RUNS = "All Runs"
    internal const val INSTANT_ROOM_UPDATE_SOLO_CLEARS = "Solo Clears"
    internal const val INSTANT_ROOM_UPDATE_PARTY_RUNS = "Party Runs"

    val cheaterMap by boolean("Cheater Map", false)
        .withDescription("Show all rooms on the map, including undiscovered ones.")

    val modeKey by keybind("Mode Keybind")
        .withDescription("Keybind to switch between cheat and legit map mode.")

    val keyMode by mode("Keybind Mode", "Toggle", listOf("Toggle", "Hold"))
        .withDescription("Toggle switches mode on press; Hold temporarily switches mode while held down.")
        .showIf { modeKey.isBound }

    val highlightMimic by boolean("Highlight Mimic", true)
        .withDescription("Highlight the mimic room on the map.")
        .showIf { cheaterMap.value || modeKey.isBound }

    val instantRoomUpdate by boolean("Instant Room Update", true)
        .withDescription("Update entered rooms and their neighbors without waiting for the hotbar map.")

    val instantRoomUpdateScope by mode(
        "Instant Room Update Scope", INSTANT_ROOM_UPDATE_ALL_RUNS,
        listOf(INSTANT_ROOM_UPDATE_ALL_RUNS, INSTANT_ROOM_UPDATE_SOLO_CLEARS, INSTANT_ROOM_UPDATE_PARTY_RUNS),
    ).withDescription("Choose which runs use instant room updates.")
        .showIf { instantRoomUpdate.value }

    val mimicColor by color("Mimic Color", Color(255, 107, 107), allowAlpha = true)
        .withDescription("Color for the mimic room highlight.")
        .showIf { highlightMimic.value }
        .group("Room Colors")

    val mimicEsp by boolean("Mimic ESP", false)
        .withDescription("Highlight the mimic chest in the world.")

    val mimicEspMode by mode("Mimic ESP Mode", "Filled Outline", listOf("Outline", "Fill", "Filled Outline"))
        .withDescription("How to render the mimic chest ESP.")
        .showIf { mimicEsp.value }

    val mimicEspColor by color("Mimic ESP Color", Color(255, 0, 0, 80), allowAlpha = true)
        .withDescription("Color for the mimic chest ESP.")
        .showIf { mimicEsp.value }

    fun isCheaterActive(): Boolean {
        if (!DungeonMap.enabled) return false
        if (keyMode.value == "Hold" && modeKey.isBound && !Platform.screenHost.anyScreenOpen) {
            return if (modeKey.isPressed) !cheaterMap.value else cheaterMap.value
        }
        return cheaterMap.value
    }

    override val showsUnrevealed: Boolean get() = isCheaterActive()
    override val keepsOptimisticRooms: Boolean get() = isInstantRoomUpdateActive()
    override val mimicTint: Color? get() = if (isCheaterActive() && highlightMimic.value) mimicColor.value else null

    internal fun instantRoomUpdateScopeMatches(scope: String, teammateCount: Int): Boolean = when (scope) {
        INSTANT_ROOM_UPDATE_SOLO_CLEARS -> teammateCount == 0
        INSTANT_ROOM_UPDATE_PARTY_RUNS -> teammateCount > 0
        else -> true
    }

    private fun isInstantRoomUpdateActive(): Boolean =
        DungeonMap.enabled && instantRoomUpdate.value && instantRoomUpdateScopeMatches(
            instantRoomUpdateScope.value, Dungeon.team.others.size,
        )

    override fun init() {
        MapOverrides.register(this)

        onKeybind(modeKey, predicate = { host.enabled && keyMode.value == "Toggle" }) {
            cheaterMap.value = !cheaterMap.value
        }

        register<RenderWorldEvent> {
            if (!LocationUtils.inDungeonRooms) return@register
            if (!mimicEsp.value) return@register
            if (Dungeon.score.mimicKilled) return@register
            val mimicRoom = Dungeon.layout.mimicRoom ?: return@register
            val color = mimicEspColor.value
            val outline = mimicEspMode.value == "Outline" || mimicEspMode.value == "Filled Outline"
            val fill = mimicEspMode.value == "Fill" || mimicEspMode.value == "Filled Outline"

            for ((cx, cy, cz) in mimicRoom.mimicChests) {
                if (outline) {
                    Platform.worldRenderer.boxEdges(
                        cx.toDouble(), cy.toDouble(), cz.toDouble(),
                        cx + 1.0, cy + 1.0, cz + 1.0,
                        color, 2f, true,
                    )
                }
                if (fill) {
                    Platform.worldRenderer.filledBox(
                        cx.toDouble(), cy.toDouble(), cz.toDouble(),
                        cx + 1.0, cy + 1.0, cz + 1.0,
                        color, true,
                    )
                }
            }
        }
    }
}
