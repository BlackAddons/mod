package org.blackaddons.core.actions

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import org.blackaddons.api.actions.StepFlag
import org.blackaddons.api.actions.StepType
import org.blackaddons.api.input.InputBinding
import org.blackaddons.api.input.InputType
import org.blackaddons.api.skyblock.LocationUtils
import org.blackaddons.api.skyblock.dungeon.DungeonFloor
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.util.containsIgnoreCase
import org.blackaddons.api.util.equalsIgnoreCase
import org.blackaddons.api.util.secondsToTicks
import org.blackaddons.core.actions.steps.Conditional
import org.blackaddons.core.actions.steps.ControlStep
import org.blackaddons.core.actions.steps.WaitStep
import org.blackaddons.core.actions.steps.adoptShownClass
import java.util.UUID

internal enum class ConditionType(val displayName: String) {
    LOCATION("Location"),
    NEAR("Near a Point"),
    ITEM("Item"),
    DUNGEON_CLASS("Dungeon Class"),
}

/**
 * Who sets off a waypoint trigger, or who one of its steps runs for: u, other players nearby (`{player}` names them),
 * or both.
 */
enum class TriggeredBy(val displayName: String) {
    ANYONE("Anyone"),
    ME("Me"),
    OTHERS("Others");

    /** [player] is the other player that walked in, or null for u. */
    fun allows(player: String?): Boolean = when (this) {
        ANYONE -> true
        ME -> player == null
        OTHERS -> player != null
    }
}

internal enum class ItemScope(val displayName: String) {
    HAND("In Hand"),
    HOTBAR("In Hotbar"),
}

internal data class ActionCondition(
    var type: ConditionType = ConditionType.LOCATION,
    var negate: Boolean = false,
    override var areaFilters: MutableList<String> = mutableListOf(),
    override var inDungeonFilter: Boolean? = null,
    override var floorFilter: DungeonFloor? = null,
    override var inBossFilter: Boolean? = null,
    override var phaseFilter: Int? = null,
    var x: Double = 0.0,
    var y: Double = 0.0,
    var z: Double = 0.0,
    var radius: Double = 3.0,
    /** SkyBlock id or part of the item name. */
    var item: String = "",
    var itemScope: ItemScope = ItemScope.HAND,
    /** A [org.blackaddons.api.skyblock.dungeon.DungeonClass] name. */
    var dungeonClass: String = "",
) : LocationFilterProvider

internal enum class WaitMode(val displayName: String) {
    TIME("Time"),
    POSITION("Position"),
    CHAT("Chat Message"),
    DUNGEON_EVENT("Dungeon Event"),
    BOSS_BAR("Boss Bar");

    /** True when the step waits on an event and so needs a timeout. */
    val isCondition: Boolean get() = this != TIME
}

internal enum class ActionDungeonEvent(val displayName: String) {
    RUN_START("Run Start"),
    BLOOD_OPEN("Blood Door Opened"),
    BOSS_ENTER("Boss Room Entered"),
    MAXOR("F7 P1: Maxor"),
    STORM("F7 P2: Storm"),
    GOLDOR("F7 P3: Goldor"),
    NECRON("F7 P4: Necron"),
    WITHER_KING("M7 P5: Wither King"),
    RUN_END("Run End"),
}

/**
 * One step: the common fields, the [params] of its [type] and its [StepFlag]s, saved flat (see [ActionJson]). A step
 * whose type no installed addon registers keeps its saved fields as they are and doesn't run.
 */
class ActionStep internal constructor(typeId: String, params: Any) {
    var enabled: Boolean = true
    var waitForPrevious: Boolean = true

    // Anyone when a save has no value, so steps saved before Me became the default keep running for everyone.
    var triggeredBy: TriggeredBy = TriggeredBy.ANYONE
    var delaySeconds: Float = 0f

    var typeId: String = typeId
        private set

    // The type's params, or the saved JSON until the type is registered.
    private var stored: Any = params

    /** Set when saved params had to fall back to defaults, for example an enum value from a newer version. */
    internal var lossy: Boolean = false
        private set

    // Saved fields that are neither common nor params: flags, and whatever a newer version or a missing addon wrote.
    private var extraFields = JsonObject()

    internal val extras: JsonObject
        get() {
            params
            return extraFields
        }

    val type: StepType<*>? get() = StepTypes.byId(typeId)

    /** The params of [type], or the saved JSON while [type] is null. */
    val params: Any
        get() {
            val raw = stored as? JsonObject ?: return stored
            val type = type ?: return raw
            val decoded = StepTypes.decode(type, raw)
            lossy = decoded.lossy
            stored = decoded.params
            extraFields = decoded.extras
            return stored
        }

    val delayTicks: Int get() = delaySeconds.secondsToTicks

    @Suppress("UNCHECKED_CAST")
    fun <P : Any> paramsOf(type: StepType<P>): P? = if (typeId == type.id) params as P else null

    fun has(flag: StepFlag): Boolean = extras[flag.id]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean == true

    fun set(flag: StepFlag, on: Boolean) {
        if (on) extras.addProperty(flag.id, true) else extras.remove(flag.id)
    }

    internal val flagIds: Set<String>
        get() = extras.entrySet().filter { (_, value) -> value.isJsonPrimitive && value.asJsonPrimitive.isBoolean && value.asBoolean }.mapTo(HashSet()) { it.key }

    /** Turns this step into a new [type] step. The common fields and flags stay. */
    fun <P : Any> retype(type: StepType<P>, params: P = type.newParams()) {
        typeId = type.id
        stored = params
        lossy = false
    }

    /** Control steps always run so blocks keep their END. */
    fun runsFor(player: String?): Boolean = enabled && (type is ControlStep || triggeredBy.allows(player))

    /** A deep copy that shares nothing with this step. */
    fun copy(): ActionStep = ActionJson.copy(this)

    /** Copies everything from [other] into this object, which the editors and the graph hold on to. */
    internal fun assign(other: ActionStep) {
        enabled = other.enabled
        waitForPrevious = other.waitForPrevious
        triggeredBy = other.triggeredBy
        delaySeconds = other.delaySeconds
        typeId = other.typeId
        stored = other.stored
        lossy = other.lossy
        extraFields = other.extraFields
    }

    companion object {
        fun <P : Any> of(
            type: StepType<P>,
            params: P = type.newParams(),
            enabled: Boolean = true,
            triggeredBy: TriggeredBy = TriggeredBy.ME,
        ): ActionStep = ActionStep(type.id, params).also {
            it.enabled = enabled
            it.triggeredBy = triggeredBy
        }
    }
}

/** Sound, title and toast an action plays. Mutable, so the editor can write to it for any action kind. */
internal interface ActionFeedbackProvider {
    var soundId: String
    var volume: Float
    var pitch: Float
    var title: String
    var subtitle: String
    var durationSeconds: Float
    var showNotification: Boolean
    var notificationTitle: String
    var notificationMessage: String
}

internal data class ActionFeedback(
    override var soundId: String = "",
    override var volume: Float = 1.0f,
    override var pitch: Float = 1.0f,
    override var title: String = "",
    override var subtitle: String = "",
    override var durationSeconds: Float = 2.0f,
    override var showNotification: Boolean = false,
    override var notificationTitle: String = "",
    override var notificationMessage: String = "",
) : ActionFeedbackProvider

/** A null group means ungrouped. */
internal interface Groupable {
    val id: UUID
    var groupId: UUID?
}

internal data class ChatAction(
    override val id: UUID = UUID.randomUUID(),
    override var groupId: UUID? = null,
    var pattern: String = "",
    var isRegex: Boolean = false,
    var ignoreOwnMessages: Boolean = false,
    override var soundId: String = "",
    override var volume: Float = 1.0f,
    override var pitch: Float = 1.0f,
    var enabled: Boolean = true,
    override var title: String = "",
    override var subtitle: String = "",
    override var durationSeconds: Float = 2.0f,
    override var showNotification: Boolean = false,
    override var notificationTitle: String = "",
    override var notificationMessage: String = "",
    var actions: MutableList<ActionStep> = mutableListOf(),
) : ActionFeedbackProvider, Groupable

internal enum class WaypointShape {
    CYLINDER, BOX
}

internal data class WaypointAction(
    var enabled: Boolean = true,
    var triggerOnEntry: Boolean = true,
    var triggerOnExit: Boolean = false,
    var triggerOnGuiExit: Boolean = false,
    var triggeredBy: TriggeredBy = TriggeredBy.ME,
    override var soundId: String = "",
    override var volume: Float = 1.0f,
    override var pitch: Float = 1.0f,
    override var title: String = "",
    override var subtitle: String = "",
    override var durationSeconds: Float = 2.0f,
    override var showNotification: Boolean = false,
    override var notificationTitle: String = "",
    override var notificationMessage: String = "",
    var actions: MutableList<ActionStep> = mutableListOf(),
) : ActionFeedbackProvider

internal data class WaypointDefinition(
    override val id: UUID = UUID.randomUUID(),
    override var groupId: UUID? = null,
    var name: String = "",
    var x: Double = 0.0,
    var y: Double = 0.0,
    var z: Double = 0.0,
    var enabled: Boolean = true,
    var colorRgb: Int = Theme.randomColorRgb(),
    var radius: Double = 1.0,
    var height: Double = 1.0,
    var showBeacon: Boolean = false,
    var showShape: Boolean = true,
    var showFullShape: Boolean = false,
    var showText: Boolean = true,
    var shape: WaypointShape = WaypointShape.CYLINDER,
    var reuseCooldownSeconds: Float = 0.0f,
    var actions: MutableList<WaypointAction> = mutableListOf(),
) : Groupable {
    @SerializedName("triggerByOthers") private var legacyTriggerByOthers: Boolean? = null

    /** Whether we have to watch other players' positions for this waypoint at all. */
    val watchesOthers: Boolean get() = actions.any { it.enabled && it.triggeredBy != TriggeredBy.ME }

    /**
     * The old waypoint-wide "by others" flag let anyone set off every trigger. Each step still says who it runs for.
     */
    internal fun adoptLegacyTriggers() {
        if (legacyTriggerByOthers == true) actions.forEach { it.triggeredBy = TriggeredBy.ANYONE }
        legacyTriggerByOthers = null
    }
}

/** Mutable, so the editor can write to it. */
internal interface LocationFilterProvider {
    var areaFilters: MutableList<String>
    var inDungeonFilter: Boolean?
    var floorFilter: DungeonFloor?
    var inBossFilter: Boolean?
    var phaseFilter: Int?
}

internal fun matchesLocationFilter(
    areaFilters: List<String>,
    inDungeonFilter: Boolean?,
    floorFilter: DungeonFloor?,
    inBossFilter: Boolean?,
    phaseFilter: Int?,
): Boolean {
    if (areaFilters.isNotEmpty()) {
        val curArea = LocationUtils.area
        if (curArea == null || areaFilters.none { it.equalsIgnoreCase(curArea) || curArea.containsIgnoreCase(it) || it.containsIgnoreCase(curArea) }) return false
    }
    if (inDungeonFilter != null && LocationUtils.inDungeon != inDungeonFilter) return false
    if (floorFilter != null && floorFilter != LocationUtils.dungeonFloor) return false
    if (inBossFilter != null && LocationUtils.inBoss != inBossFilter) return false
    val phase = phaseFilter
    if (phase != null && phase > 0 && LocationUtils.f7Phase?.number != phase) return false
    return true
}

internal data class ActionGroup(
    val id: UUID = UUID.randomUUID(),
    var name: String = "",
    var enabled: Boolean = true,
    var collapsed: Boolean = false,
    override var areaFilters: MutableList<String> = mutableListOf(),
    override var inDungeonFilter: Boolean? = null,
    override var floorFilter: DungeonFloor? = null,
    override var inBossFilter: Boolean? = null,
    override var phaseFilter: Int? = null,
) : LocationFilterProvider {
    fun isActive(): Boolean = enabled && matchesLocationFilter(areaFilters, inDungeonFilter, floorFilter, inBossFilter, phaseFilter)
}

internal data class ShortcutAction(
    override val id: UUID = UUID.randomUUID(),
    override var groupId: UUID? = null,
    var name: String = "",
    var alias: String = "",
    var key: InputBinding = InputBinding.NONE,
    var onlyInGame: Boolean = true,
    var enabled: Boolean = true,
    var actions: MutableList<ActionStep> = mutableListOf(),
) : Groupable {
    @SerializedName("keyCode") private var legacyKeyCode: Int? = null
    @SerializedName("keyType") private var legacyKeyType: InputType? = null

    val isBound: Boolean get() = key.isBound

    fun adoptLegacyKey() {
        val code = legacyKeyCode ?: return
        key = InputBinding.legacy(legacyKeyType ?: InputType.KEYBOARD, code)
        legacyKeyCode = null
        legacyKeyType = null
    }
}

internal data class LocationAction(
    override val id: UUID = UUID.randomUUID(),
    override var groupId: UUID? = null,
    var name: String = "",
    var enabled: Boolean = true,
    override var areaFilters: MutableList<String> = mutableListOf(),
    override var inDungeonFilter: Boolean? = null,
    override var floorFilter: DungeonFloor? = null,
    override var inBossFilter: Boolean? = null,
    override var phaseFilter: Int? = null,
    /** Fires on this event while the filters match. Null means on entry. */
    var triggerEvent: ActionDungeonEvent? = null,
    var delaySeconds: Float = 1.0f,
    /** Delay in server ticks from the trigger. Replaces [delaySeconds] when it's not 0. */
    var delayServerTicks: Int = 0,
    var cooldownSeconds: Float = 5.0f,
    var actions: MutableList<ActionStep> = mutableListOf(),
) : LocationFilterProvider, Groupable {
    fun matchesLocation(): Boolean = enabled && matchesLocationFilter(areaFilters, inDungeonFilter, floorFilter, inBossFilter, phaseFilter)
}

internal data class ActionProfile(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "Default",
    val chatActions: MutableList<ChatAction> = mutableListOf(),
    val waypoints: MutableList<WaypointDefinition> = mutableListOf(),
    val waypointGroups: MutableList<ActionGroup> = mutableListOf(),
    val shortcuts: MutableList<ShortcutAction> = mutableListOf(),
    val locationActions: MutableList<LocationAction> = mutableListOf(),
    val chatGroups: MutableList<ActionGroup> = mutableListOf(),
    val shortcutGroups: MutableList<ActionGroup> = mutableListOf(),
    val locationGroups: MutableList<ActionGroup> = mutableListOf(),
) {
    fun allGroups(): Sequence<ActionGroup> = sequenceOf(chatGroups, waypointGroups, shortcutGroups, locationGroups).flatten()

    fun allGroupables(): Sequence<Groupable> = sequenceOf(chatActions, waypoints, shortcuts, locationActions).flatten()

    fun adoptLegacy(): ActionProfile = apply {
        shortcuts.forEach(ShortcutAction::adoptLegacyKey)
        waypoints.forEach(WaypointDefinition::adoptLegacyTriggers)
        allSteps().forEach { step ->
            step.paramsOf(WaitStep)?.adoptLegacy()
            (step.params as? Conditional)?.condition?.adoptShownClass()
        }
    }

    fun allSteps(): Sequence<ActionStep> = sequence {
        chatActions.forEach { yieldAll(it.actions) }
        waypoints.forEach { wp -> wp.actions.forEach { yieldAll(it.actions) } }
        shortcuts.forEach { yieldAll(it.actions) }
        locationActions.forEach { yieldAll(it.actions) }
    }
}

internal data class ActionProfileHeader(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "Default",
)

internal data class ActionProfileManifest(
    var activeProfileId: String = "",
    val profiles: MutableList<ActionProfileHeader> = mutableListOf(),
)
