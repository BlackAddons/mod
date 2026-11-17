package org.blackaddons.core.dungeon

import org.blackaddons.api.chat.ChatMessage
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.chat.ChatMessageType
import org.blackaddons.api.event.chat.ChatReceiveEvent
import org.blackaddons.api.event.dungeon.F7PhaseStartEvent
import org.blackaddons.api.event.subscribe
import org.blackaddons.api.event.world.BossBarEvent
import org.blackaddons.api.skyblock.SkyblockHost
import org.blackaddons.api.skyblock.dungeon.DungeonFloor
import org.blackaddons.api.skyblock.dungeon.F7Phase
import org.blackaddons.api.testing.TestPlatform
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class F7BossTest {

    private val started = mutableListOf<F7Phase>()

    @BeforeEach
    fun setUp() {
        floor(DungeonFloor.F7)
        DungeonTracker.start()
        DungeonTracker.reset()
        started.clear()
        EventBus.subscribe<F7PhaseStartEvent> { started += event.phase }
    }

    @Test
    fun `each boss's opening line starts its phase once`() {
        chat("[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!")
        chat("[BOSS] Storm: Pathetic Maxor, just like expected.")
        chat("[BOSS] Storm: Pathetic Maxor, just like expected.")
        chat("[BOSS] Goldor: Who dares trespass into my domain?")
        chat("[BOSS] Necron: Finally, I heard so much about you. The Eye likes you very much.")

        assertEquals(F7Phase.NECRON, F7Boss.phase)
        assertEquals(listOf(F7Phase.MAXOR, F7Phase.STORM, F7Phase.GOLDOR, F7Phase.NECRON), started)
    }

    @Test
    fun `the Wither King's bar starts phase five on Master 7 only`() {
        bossBar("Wither King")
        assertNull(F7Boss.phase)

        floor(DungeonFloor.M7)
        bossBar("Wither King")
        assertEquals(F7Phase.WITHER_KING, F7Boss.phase)
    }

    @Test
    fun `a reset forgets the phase`() {
        chat("[BOSS] Goldor: Who dares trespass into my domain?")
        DungeonTracker.reset()
        assertNull(F7Boss.phase)
    }

    private fun chat(text: String) =
        EventBus.post(ChatReceiveEvent(text, text, ChatMessageType.SYSTEM) { ChatMessage(emptyList()) })

    private fun bossBar(name: String) = EventBus.post(BossBarEvent.Update(UUID(0, 0), name, 1f))

    private fun floor(name: DungeonFloor) {
        TestPlatform.skyblock = object : SkyblockHost {
            override val onSkyblock: Boolean = true
            override val location: String = "The Catacombs"
            override val profile: String? = null
            override val accountId: String? = null
            override val dungeonFloor: DungeonFloor = name
            override val inRift: Boolean = false
        }
    }
}
