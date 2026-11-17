package org.blackaddons.core.actions

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import org.blackaddons.api.actions.StepFlag
import org.blackaddons.api.actions.StepType
import org.blackaddons.core.actions.steps.*
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** Every known [StepType] and [StepFlag]: the core's own, plus the ones discovery finds in addons. */
internal object StepTypes {
    private val byId = ConcurrentHashMap<String, StepType<*>>()
    private val registered = CopyOnWriteArrayList<StepType<*>>()
    private val flags = CopyOnWriteArrayList<StepFlag>()
    private val defaults = ConcurrentHashMap<Class<*>, Any>()

    /* TODO: inventory steps in actions/inventory (equip, summon pet), built on SkyblockMenus and ContainerTasks. */
    init {
        listOf(WaitStep, HudTimerStep, TextOverlayStep, SendMessageStep, TempWaypointStep, RepeatStep, IfStep, ElseStep, EndStep, StopStepsStep)
            .forEach { type -> register(type)?.let { error("Built-in step ${type.id}: $it") } }
    }

    /** Menu order: by category, then in registration order. */
    val all: List<StepType<*>> get() = registered.sortedBy { it.category.ordinal }

    fun byId(id: String): StepType<*>? = byId[id]

    /** Null when accepted, otherwise why not. Registering the same object twice is a no-op. */
    fun register(type: StepType<*>): String? {
        if (type.id.isBlank()) return "it has a blank id"
        byId[type.id]?.let { existing -> return if (existing === type) null else "id '${type.id}' is taken by ${existing::class.java.name}" }
        val params = runCatching { type.newParams() }.getOrElse { return "newParams() threw $it" }
        val defaults = defaultsOf(params.javaClass) ?: return "${params.javaClass.name} has no no-arg constructor (give every field a default)"
        val taken = ActionJson.STEP_COMMON + flags.map { it.id }
        val clash = jsonFields(defaults.javaClass).map { it.jsonName }.filter { it in taken }
        if (clash.isNotEmpty()) return "its params reuse the common step fields or flags $clash"
        if (byId.putIfAbsent(type.id, type) != null) return "id '${type.id}' was registered concurrently"
        registered += type
        return null
    }

    /** The flags that apply to [type]. */
    fun flagsFor(type: StepType<*>?): List<StepFlag> = if (type == null) emptyList() else flags.filter { flag ->
        runCatching { flag.appliesTo(type) }.getOrDefault(false)
    }

    /** Null when accepted, otherwise why not. Registering the same object twice is a no-op. */
    fun register(flag: StepFlag): String? {
        if (flag.id.isBlank()) return "it has a blank id"
        if (flag.id in ActionJson.STEP_COMMON) return "'${flag.id}' is a common step field"
        flags.firstOrNull { it.id == flag.id }?.let { existing -> return if (existing === flag) null else "id '${flag.id}' is taken by ${existing::class.java.name}" }
        registered.firstOrNull { type -> flag.id in paramNames(type) }?.let { return "step type ${it.id} has a param named '${flag.id}'" }
        flags += flag
        return null
    }

    class Decoded(val params: Any, val lossy: Boolean, val extras: JsonObject)

    /**
     * Missing fields read as the class defaults, like old saves expect. Nulls in non-null fields fall back to the
     * defaults too.
     */
    fun decode(type: StepType<*>, raw: JsonObject): Decoded {
        val cls = type.newParams().javaClass
        val names = jsonFields(cls).map { it.jsonName }.toSet()
        val extras = JsonObject().also { extras -> raw.entrySet().forEach { (name, element) -> if (name !in names && name !in ActionJson.LEGACY_STEP_FIELDS) extras.add(name, element) } }
        val params = ActionJson.plain.fromJson(raw, cls) ?: return Decoded(type.newParams(), lossy = true, extras)
        val fallback = defaultsOf(cls) ?: return Decoded(params, lossy = false, extras)
        var lossy = false
        for (field in jsonFields(cls)) {
            if (field.get(params) != null) continue
            val default = field.get(fallback) ?: continue
            field.set(params, default)
            lossy = true
        }
        return Decoded(params, lossy, extras)
    }

    /** What a field missing from the JSON reads as. Compacting drops fields that equal it. */
    fun defaultsOf(cls: Class<*>): Any? = defaults[cls] ?: runCatching {
        cls.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
    }.getOrNull()?.also { defaults[cls] = it }

    private fun paramNames(type: StepType<*>): Set<String> =
        runCatching { jsonFields(type.newParams().javaClass).map { it.jsonName }.toSet() }.getOrDefault(emptySet())

    private val Field.jsonName: String get() = getAnnotation(SerializedName::class.java)?.value ?: name

    private fun jsonFields(cls: Class<*>): List<Field> = cls.declaredFields
        .filter { !Modifier.isStatic(it.modifiers) && !Modifier.isTransient(it.modifiers) }
        .onEach { it.isAccessible = true }
}
