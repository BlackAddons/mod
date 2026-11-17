package org.blackaddons.core.actions

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import org.blackaddons.api.input.InputBinding

/**
 * Gson for action files and share strings. It only writes non-default fields and reads missing fields as defaults, so
 * old and new builds stay compatible. A step is one flat object: the common fields, `type`, then the fields of its
 * type's params.
 */
internal object ActionJson {

    fun gson(pretty: Boolean = false): Gson = GsonBuilder()
        .apply { if (pretty) setPrettyPrinting() }
        .registerTypeAdapter(InputBinding::class.java, InputBinding.GsonAdapter)
        .registerTypeAdapterFactory(CompactActions)
        .create()

    /** For params. The step adapter compacts them itself. */
    val plain: Gson by lazy { gson() }

    internal val STEP_COMMON = setOf("enabled", "waitForPrevious", "triggeredBy", "type", "delaySeconds")

    /**
     * Every field of the single step class old builds had. Very old files wrote all of them on every step, so a step
     * drops the ones its type doesn't read instead of carrying them around forever as unknown fields.
     */
    internal val LEGACY_STEP_FIELDS = setOf(
        "slotIndex", "message", "durationSeconds", "yaw", "pitch", "useCoordinates", "targetX", "targetY", "targetZ",
        "rotationSpeed", "lookAtSeconds", "instaSnap", "lookAfterAlign", "alignPostYaw", "alignPostPitch", "useLookAfterCoords",
        "alignLookAtX", "alignLookAtY", "alignLookAtZ", "dontWaitForPerfectAlign", "movementKeybinds", "waitMode", "waitRadius",
        "waitTimeoutSeconds", "stopOnTimeout", "waitText", "waitRegex", "waitEvent", "repeatCount", "repeatWhile", "condition",
        "waypointCoords", "waypointColor", "waypointBeacon", "waypointTracer", "waypointDepthCheck", "waypointArrivalRadius",
        "waypointExpireSeconds", "waitPosition", "collapsed",
    )

    // Old builds wrote no `type` for their default type.
    private const val LEGACY_DEFAULT_TYPE = "SWITCH_SLOT"

    internal val CONDITION_COMMON = setOf("type", "negate")

    internal val CONDITION_FIELDS: Map<ConditionType, Set<String>> = mapOf(
        ConditionType.LOCATION to setOf("areaFilters", "inDungeonFilter", "floorFilter", "inBossFilter", "phaseFilter"),
        ConditionType.NEAR to setOf("x", "y", "z", "radius"),
        ConditionType.ITEM to setOf("item", "itemScope"),
        ConditionType.DUNGEON_CLASS to setOf("dungeonClass"),
    )

    fun copy(step: ActionStep): ActionStep = plain.fromJson(plain.toJsonTree(step), ActionStep::class.java)

    // Null means every field, for a condition type this build doesn't know.
    private fun relevant(value: Any): Set<String>? = when (value) {
        is ActionCondition -> CONDITION_FIELDS[value.type as ConditionType?]?.let { CONDITION_COMMON + it }
        else -> null
    }

    private class StepAdapter(private val gson: Gson) : TypeAdapter<ActionStep>() {
        private val elements = gson.getAdapter(JsonElement::class.java)
        private val blank = ActionStep(LEGACY_DEFAULT_TYPE, JsonObject())

        override fun write(out: JsonWriter, value: ActionStep?) {
            if (value == null) {
                out.nullValue()
                return
            }
            val tree = JsonObject()
            if (value.enabled != blank.enabled) tree.addProperty("enabled", value.enabled)
            if (value.waitForPrevious != blank.waitForPrevious) tree.addProperty("waitForPrevious", value.waitForPrevious)
            if (value.triggeredBy != blank.triggeredBy) tree.add("triggeredBy", gson.toJsonTree(value.triggeredBy))
            tree.addProperty("type", value.typeId)
            if (value.delaySeconds != blank.delaySeconds) tree.addProperty("delaySeconds", value.delaySeconds)
            params(value.params).entrySet().forEach { (name, element) -> tree.add(name, element) }
            value.extras.entrySet().forEach { (name, element) -> tree.add(name, element) }
            elements.write(out, tree)
        }

        private fun params(params: Any): JsonObject {
            if (params is JsonObject) return params
            val tree = gson.toJsonTree(params).asJsonObject
            val defaults = StepTypes.defaultsOf(params.javaClass)?.let { gson.toJsonTree(it).asJsonObject } ?: return tree
            tree.entrySet().removeIf { (name, element) -> defaults[name] == element }
            return tree
        }

        override fun read(reader: JsonReader): ActionStep? {
            val tree = elements.read(reader)?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
            val typeId = tree["type"]?.takeIf { it.isJsonPrimitive }?.asString ?: LEGACY_DEFAULT_TYPE
            val raw = JsonObject().also { params -> tree.entrySet().forEach { (name, element) -> if (name !in STEP_COMMON) params.add(name, element) } }
            return ActionStep(typeId, raw).apply {
                enabled = tree.boolean("enabled") ?: blank.enabled
                waitForPrevious = tree.boolean("waitForPrevious") ?: blank.waitForPrevious
                triggeredBy = tree["triggeredBy"]?.let { runCatching { gson.fromJson(it, TriggeredBy::class.java) }.getOrNull() } ?: blank.triggeredBy
                delaySeconds = tree["delaySeconds"]?.takeIf { it.isJsonPrimitive }?.asFloat ?: blank.delaySeconds
            }
        }

        private fun JsonObject.boolean(name: String): Boolean? = get(name)?.takeIf { it.isJsonPrimitive }?.asBoolean
    }

    private object CompactActions : TypeAdapterFactory {
        private val compacted = setOf(
            ActionCondition::class.java, ChatAction::class.java, WaypointAction::class.java,
            WaypointDefinition::class.java, ActionGroup::class.java, ShortcutAction::class.java, LocationAction::class.java,
            ActionProfile::class.java,
        )

        override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
            @Suppress("UNCHECKED_CAST")
            if (type.rawType == ActionStep::class.java) return StepAdapter(gson) as TypeAdapter<T>
            if (type.rawType !in compacted) return null
            val delegate = gson.getDelegateAdapter(this, type)
            val elements = gson.getAdapter(JsonElement::class.java)
            // All compacted classes have defaults for all fields, so they have a no-arg constructor.
            @Suppress("UNCHECKED_CAST")
            val defaults: JsonObject by lazy {
                delegate.toJsonTree(type.rawType.getDeclaredConstructor().newInstance() as T).asJsonObject
            }
            return object : TypeAdapter<T>() {
                override fun write(out: JsonWriter, value: T?) {
                    if (value == null) {
                        out.nullValue()
                        return
                    }
                    val tree = delegate.toJsonTree(value).asJsonObject
                    val keep = relevant(value)
                    tree.entrySet().removeIf { (name, element) -> (keep != null && name !in keep) || defaults[name] == element }
                    elements.write(out, tree)
                }

                override fun read(reader: JsonReader): T? = delegate.read(reader)
            }
        }
    }
}
