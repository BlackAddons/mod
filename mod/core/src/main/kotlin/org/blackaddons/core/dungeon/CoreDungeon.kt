package org.blackaddons.core.dungeon

import org.blackaddons.api.skyblock.dungeon.*
import org.blackaddons.core.dungeon.map.DoorTile
import org.blackaddons.core.dungeon.map.DungeonTree
import org.blackaddons.core.dungeon.map.MapState

object CoreDungeon : DungeonProvider {

    override val run = object : DungeonRun {
        override val started: Boolean get() = DungeonTracker.dungeonStarted
        override val ended: Boolean get() = DungeonTracker.dungeonEnded
        override val ticks: Long get() = DungeonTracker.currentTime
        override val startTime: DualTime? get() = DungeonTracker.dungeonStartTime
        override val bossEntryTime: DualTime? get() = DungeonTracker.bossEntryTime
        override val bloodOpenTime: DualTime? get() = DungeonTracker.bloodOpenTime
        override val watcherClearTime: DualTime? get() = DungeonTracker.watcherClearTime
        override val witherKeys: Int get() = DungeonTracker.witherKeys
        override val bloodKeys: Int get() = DungeonTracker.bloodKeys
        override val lastDoorOpener: DungeonPlayer? get() = DungeonTracker.lastDoorOpener
        override val puzzles: List<PuzzleStatus> get() = DungeonTracker.puzzles.toList()
        override val bossPhase: F7Phase? get() = F7Boss.phase
    }

    override val team = object : DungeonTeam {
        override val players: List<DungeonPlayer> get() = DungeonTracker.dungeonTeammates.toList()
    }

    override val score = object : DungeonScore {
        override val score: Int get() = ScoreCalculation.score
        override val foundSecrets: Int get() = ScoreCalculation.foundSecrets
        override val secretPercentage: Double get() = ScoreCalculation.secretPercentage
        override val totalSecrets: Int get() = ScoreCalculation.estimatedTotalSecrets()
        override val crypts: Int get() = ScoreCalculation.cryptsCount
        override val deaths: Int get() = ScoreCalculation.deathCount
        override val completedRooms: Int get() = ScoreCalculation.completedRooms
        override val clearedPercentage: Int get() = ScoreCalculation.clearedPercentage
        override val secondsElapsed: Int get() = ScoreCalculation.secondsElapsed
        override val mimicKilled: Boolean get() = ScoreCalculation.mimicKilled
        override val princeKilled: Boolean get() = ScoreCalculation.princeKilled
    }

    override val layout = object : DungeonLayout {
        override val rooms: Collection<DungeonRoom> get() = MapState.uniqueRooms.values.toList()
        override val doors: List<DungeonDoor> get() = MapState.dungeonList.filterIsInstance<DoorTile>()
        override fun roomAt(x: Double, z: Double): DungeonRoom? = MapState.getRoomAt(x, z)
        override fun isFairyExit(door: DungeonDoor): Boolean = door is DoorTile && DungeonTree.isFairy(door)
        override val mimicRoom: DungeonRoom? get() = MapState.mimicRoom
    }
}
