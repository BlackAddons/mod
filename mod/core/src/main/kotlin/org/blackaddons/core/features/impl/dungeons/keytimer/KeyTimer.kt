package org.blackaddons.core.features.impl.dungeons.keytimer

import org.blackaddons.api.event.ClientTickEvent
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.event.dungeon.DungeonDoorOpenedEvent
import org.blackaddons.api.event.dungeon.DungeonKeyPickedUpEvent
import org.blackaddons.api.event.world.EntityEvent
import org.blackaddons.api.event.world.EntityMetadataUpdateEvent
import org.blackaddons.api.event.world.EntitySnapshot
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.hud.AnchorX
import org.blackaddons.api.hud.AnchorY
import org.blackaddons.api.hud.hudTextNode
import org.blackaddons.api.skyblock.LocationUtils
import org.blackaddons.api.skyblock.dungeon.DoorType
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.util.formatDecimals
import org.blackaddons.api.util.secondsToTicks
import org.blackaddons.api.util.ticksToSeconds
import java.awt.Color
import java.util.UUID

private const val KEY_LIFETIME_SECONDS = 10f

object KeyTimer : Feature(
    description = "Shows the remaining auto-pickup window for Wither and Blood Keys.",
    name = "Key Timer",
) {
    private val state = KeyTimerState()

    @Suppress("unused")
    private val hud = hudElement(
        "Key Timer",
        shouldDraw = { state.isActive },
        defaultAnchorX = AnchorX.RIGHT,
        defaultAnchorY = AnchorY.TOP,
        defaultOffsetX = -120f,
        defaultOffsetY = 100f,
    ) { example ->
        val type = state.type ?: DoorType.WITHER
        val seconds = if (example) KEY_LIFETIME_SECONDS else state.ticksRemaining.ticksToSeconds
        hudTextNode("${type.keyLabel}: ${seconds.formatDecimals(1)}s", type.keyColor, shadow = true)
    }

    override fun init() {
        register<EntityMetadataUpdateEvent> {
            if (LocationUtils.inDungeon) state.detect(event.entity)
        }
        register<EntityEvent.Remove> {
            state.remove(event.entity.uuid)
        }
        register<DungeonKeyPickedUpEvent> {
            state.reset()
        }
        register<DungeonDoorOpenedEvent> {
            state.reset()
        }
        register<ClientTickEvent.Post> {
            state.tick()
        }
        register<WorldChangeEvent> {
            state.reset()
        }
    }

    override fun onDisable() {
        state.reset()
        super.onDisable()
    }
}

private val DoorType.keyLabel: String get() = if (this == DoorType.BLOOD) "Blood Key" else "Wither Key"

private val DoorType.keyColor: Color get() = if (this == DoorType.BLOOD) Theme.DANGER else Theme.TEXT_MUTED

internal class KeyTimerState {
    var type: DoorType? = null
        private set
    var ticksRemaining: Int = 0
        private set
    private var entityId: UUID? = null

    val isActive: Boolean get() = ticksRemaining > 0

    fun detect(entity: EntitySnapshot) {
        if (!entity.isArmorStand) return
        val keyType = DoorType.entries.firstOrNull { entity.name == it.keyLabel } ?: return
        if (entity.uuid == entityId) return
        type = keyType
        entityId = entity.uuid
        ticksRemaining = KEY_LIFETIME_SECONDS.secondsToTicks
    }

    fun remove(removedEntityId: UUID) {
        if (entityId == removedEntityId) reset()
    }

    fun tick() {
        if (!isActive) return
        ticksRemaining--
        if (ticksRemaining == 0) reset()
    }

    fun reset() {
        type = null
        entityId = null
        ticksRemaining = 0
    }
}
