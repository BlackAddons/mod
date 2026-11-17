package org.blackaddons.core.soloclears

import org.blackaddons.api.player.GameMode
import org.blackaddons.api.player.PlayerHost
import org.blackaddons.api.skyblock.SkyblockHost
import org.blackaddons.api.skyblock.dungeon.DungeonClass
import org.blackaddons.api.skyblock.dungeon.DungeonFloor
import org.blackaddons.api.skyblock.dungeon.DungeonPlayer
import org.blackaddons.api.testing.TestPlatform
import org.blackaddons.api.world.ItemInfo
import org.blackaddons.core.dungeon.DungeonTracker
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class RunPartySnapshotTest {

    private var mockSidebar: List<String> = emptyList()
    private var mockTab: List<String> = emptyList()
    private var localPlayerName: String = "TestPlayer"

    @BeforeEach
    fun setUp() {
        mockSidebar = emptyList()
        mockTab = emptyList()
        localPlayerName = "TestPlayer"

        TestPlatform.player = object : PlayerHost {
            override val isInGame: Boolean = true
            override val name: String get() = localPlayerName
            override val uuid: UUID = UUID.randomUUID()
            override val skinTextures: String? = null
            override val x: Double = 0.0
            override val y: Double = 0.0
            override val z: Double = 0.0
            override val renderX: Double = 0.0
            override val renderY: Double = 0.0
            override val renderZ: Double = 0.0
            override val yaw: Float = 0f
            override val pitch: Float = 0f
            override val isSneaking: Boolean = false
            override val onGround: Boolean = true
            override val health: Float = 20f
            override val maxHealth: Float = 20f
            override val food: Int = 20
            override val gameMode: GameMode = GameMode.SURVIVAL
            override val mainHandItem: ItemInfo? = null
            override fun inventoryItem(slot: Int): ItemInfo? = null
            override val dimensionId: String = "minecraft:overworld"
        }

        TestPlatform.skyblock = object : SkyblockHost {
            override val onSkyblock: Boolean = true
            override val location: String = "The Catacombs"
            override val profile: String? = null
            override val accountId: String? = null
            override val dungeonFloor: DungeonFloor = DungeonFloor.F7
            override val inRift: Boolean = false
            override val sidebarLines: List<String> get() = mockSidebar
            override val tabLines: List<String> get() = mockTab
        }

        DungeonTracker.start()
        DungeonTracker.reset()
    }

    @Test
    fun `real solo run with Solo scoreboard and Party (1) tab returns true`() {
        mockSidebar = listOf(
            "The Catacombs (F7)",
            "Keys: \u00a7c■ \u00a7c✗",
            "Time Elapsed: \u00a7a29s",
            "Cleared: \u00a7c0% \u00a78(0)",
            "\u00a73\u00a7lSolo\u00a7i",
            "www.hypixel.net",
        )
        mockTab = listOf(
            "         Party (1)",
            "[278] TestPlayer (Mage XLIII)",
        )

        assertTrue(RunPartySnapshot.looksSolo())
    }

    @Test
    fun `party run with 5 players including player named SoloF7 and Party (5) returns false`() {
        mockSidebar = listOf(
            "The Catacombs (F7)",
            "Keys: \u00a7c■ \u00a7c✗",
            "Time Elapsed: \u00a7a01m 05s",
            "Cleared: \u00a7c33%",
            "www.hypixel.net",
        )
        mockTab = listOf(
            "SoloF7",
            "         Party (5)",
            "[461] SoloF7 (Tank XLVII)",
            "[441] DungeonizedCat ☠ (Berserk L)",
            "[345] 24yrs ♲ (Healer XXXVIII)",
            "[620] zMusu (Archer L)",
            "[511] CatGirlMeowRune (Mage XLVIII)",
        )

        DungeonTracker.dungeonTeammates.add(DungeonPlayer("SoloF7", DungeonClass.TANK, 47))
        DungeonTracker.dungeonTeammates.add(DungeonPlayer("DungeonizedCat", DungeonClass.BERSERK, 50))
        DungeonTracker.dungeonTeammates.add(DungeonPlayer("24yrs", DungeonClass.HEALER, 38))
        DungeonTracker.dungeonTeammates.add(DungeonPlayer("zMusu", DungeonClass.ARCHER, 50))
        DungeonTracker.dungeonTeammates.add(DungeonPlayer("TestPlayer", DungeonClass.MAGE, 48))

        assertFalse(RunPartySnapshot.looksSolo())
    }

    @Test
    fun `party run returns false even if teammates list has not populated yet when tab has Party (5)`() {
        mockSidebar = listOf(
            "The Catacombs (F7)",
            "Time Elapsed: \u00a7a01m 05s",
            "www.hypixel.net",
        )
        mockTab = listOf(
            "SoloF7",
            "         Party (5)",
            "[461] SoloF7 (Tank XLVII)",
        )

        assertFalse(RunPartySnapshot.looksSolo())
    }

    @Test
    fun `party run returns false when teammates are present even if sidebar has solo`() {
        mockSidebar = listOf("\u00a73\u00a7lSolo\u00a7i")
        mockTab = listOf("         Party (1)")
        DungeonTracker.dungeonTeammates.add(DungeonPlayer("Teammate", DungeonClass.MAGE, 50))

        assertFalse(RunPartySnapshot.looksSolo())
    }

    @Test
    fun `solo run where local player is named SoloF7 returns true`() {
        localPlayerName = "SoloF7"
        mockSidebar = listOf("\u00a73\u00a7lSolo\u00a7i")
        mockTab = listOf(
            "         Party (1)",
            "[461] SoloF7 (Tank XLVII)",
        )
        DungeonTracker.dungeonTeammates.add(DungeonPlayer("SoloF7", DungeonClass.TANK, 47))

        assertTrue(RunPartySnapshot.looksSolo())
    }

    @Test
    fun `empty sidebar and tab returns false`() {
        mockSidebar = emptyList()
        mockTab = emptyList()

        assertFalse(RunPartySnapshot.looksSolo())
    }

    @Test
    fun `tab with Party (1) returns true even when sidebar is empty`() {
        mockSidebar = emptyList()
        mockTab = listOf("         Party (1)")

        assertTrue(RunPartySnapshot.looksSolo())
    }

    @Test
    fun `sidebar with Solo returns true even when tab is empty`() {
        mockSidebar = listOf("§3§lSolo")
        mockTab = emptyList()

        assertTrue(RunPartySnapshot.looksSolo())
    }

    @Test
    fun `party run with Party (2) returns false`() {
        mockSidebar = listOf("The Catacombs (F7)")
        mockTab = listOf("         Party (2)")

        assertFalse(RunPartySnapshot.looksSolo())
    }

    @Test
    fun `line merely containing the word solo as part of a longer text does not trigger solo`() {
        mockSidebar = listOf(
            "The Catacombs (F7)",
            "SoloPlayer defeated Livid",
            "www.hypixel.net",
        )
        mockTab = listOf("Player list")

        assertFalse(RunPartySnapshot.looksSolo())
    }
}
