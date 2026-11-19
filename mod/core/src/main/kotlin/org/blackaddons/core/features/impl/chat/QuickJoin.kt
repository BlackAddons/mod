package org.blackaddons.core.features.impl.chat

import org.blackaddons.api.Platform
import org.blackaddons.api.feature.Feature

private val floors = listOf("one", "two", "three", "four", "five", "six", "seven")
private val kuudraTiers = listOf("normal", "hot", "burning", "fiery", "infernal")

internal val quickJoinInstances: Map<String, String> = buildMap {
    put("f0", "catacombs_entrance")
    floors.forEachIndexed { index, floor ->
        put("f${index + 1}", "catacombs_floor_$floor")
        put("m${index + 1}", "master_catacombs_floor_$floor")
    }
    kuudraTiers.forEachIndexed { index, tier -> put("t${index + 1}", "kuudra_$tier") }
}

/** Off Skyblock or while disabled the typed command reaches the server unchanged. */
object QuickJoin : Feature(
    description = "Join dungeon floors and Kuudra tiers with /f0-/f7, /m1-/m7 and /t1-/t5.",
    name = "Quick Join",
) {

    override fun init() {
        for ((alias, instance) in quickJoinInstances) {
            Platform.commands.register(alias) {
                execute {
                    Platform.chat.sendCommand(if (enabled && Platform.skyblock.onSkyblock) "joininstance $instance" else alias)
                }
            }
        }
    }
}
