package org.blackaddons.api.testing

import org.blackaddons.api.skyblock.dungeon.Dungeon
import org.blackaddons.api.skyblock.dungeon.DungeonLayout
import org.blackaddons.api.skyblock.dungeon.DungeonPlayer
import org.blackaddons.api.skyblock.dungeon.DungeonProvider
import org.blackaddons.api.skyblock.dungeon.DungeonRun
import org.blackaddons.api.skyblock.dungeon.DungeonScore
import org.blackaddons.api.skyblock.dungeon.DungeonTeam

/** Test [DungeonProvider], bound on first use. The party is a plain list; other views throw until a fake is set. */
object TestDungeon : DungeonProvider {
    val players = mutableListOf<DungeonPlayer>()

    override val team = object : DungeonTeam {
        override val players: List<DungeonPlayer> get() = TestDungeon.players.toList()
    }
    override lateinit var run: DungeonRun
    override lateinit var score: DungeonScore
    override lateinit var layout: DungeonLayout

    init {
        Dungeon.bind(this)
    }
}
