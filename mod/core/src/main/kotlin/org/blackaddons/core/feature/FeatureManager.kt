package org.blackaddons.core.feature

import io.github.classgraph.ClassGraph
import io.github.classgraph.ClassInfo
import org.blackaddons.api.Addon
import org.blackaddons.api.actions.StepFlag
import org.blackaddons.api.actions.StepType
import org.blackaddons.api.event.EventListener
import org.blackaddons.api.feature.CategoryRegistry
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.feature.FeatureCategory
import org.blackaddons.api.feature.FeatureExtension
import org.blackaddons.api.feature.annotations.DevOnly
import org.blackaddons.api.hud.HudElement
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.dev.DevFlags
import org.blackaddons.core.actions.StepTypes
import org.blackaddons.core.log.Log
import java.util.IdentityHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Finds [Feature] objects with ClassGraph under the host packages and each registered [Addon]'s package, then wires
 * listeners and HUD elements. Action [StepType] and [StepFlag] objects get found in the same scan. Call [init] once at
 * client init.
 */
object FeatureManager {
    /**
     * Only host features are auto-discovered. Addons (cheat too) have to use the `blackaddons:addon` entrypoint. Keep
     * these package names in sync, or the ClickGUI is empty.
     */
    private val HOST_PACKAGES = listOf("org.blackaddons.core", "org.blackaddons.platform")
    private const val FEATURE_FQN = "org.blackaddons.api.feature.Feature"
    private const val EXTENSION_FQN = "org.blackaddons.api.feature.FeatureExtension"
    private const val STEP_TYPE_FQN = "org.blackaddons.api.actions.StepType"
    private const val STEP_FLAG_FQN = "org.blackaddons.api.actions.StepFlag"

    val features = linkedSetOf<Feature>()
    val hudElements: MutableList<HudElement> = CopyOnWriteArrayList()

    private val addonIds = mutableMapOf<Feature, String>()
    private val extensions = mutableMapOf<Feature, MutableList<FeatureExtension>>()
    private val hudAddonIds = mutableMapOf<HudElement, String>()

    var loadedAddons: List<String> = emptyList()
        private set

    private var initialized = false

    /** Null for first-party. Gives config keys a namespace, so classes with the same name don't collide. */
    fun addonIdOf(feature: Feature): String? = addonIds[feature]

    fun init(registrations: List<AddonRegistration> = emptyList()) {
        if (initialized) {
            Log.main.error("FeatureManager.init called more than once - ignoring the duplicate call")
            return
        }
        initialized = true

        val acceptedRegistrations = validateAddons(registrations)
        /*
         * Also before the scan, because a feature object might resolve its strings while it's built. It uses the
         * declared id, because the cheat addon in the fat jar has the mod id "blackaddons". Worst case, some
         * translations are missing.
         */
        acceptedRegistrations.forEach { I18n.register(it.addon.id) }
        // Before the scan, because feature objects built by it might already log through Addon.log.
        synchronized(addonModIds) { acceptedRegistrations.forEach { addonModIds[it.addon] = it.modId } }
        val accepted = acceptedRegistrations.map { it.addon }
        registerAddonCategories(accepted)
        val registrationValidator = FeatureRegistrationValidator()
        val packages = (HOST_PACKAGES + accepted.map { it::class.java.packageName })
            .filter { it.isNotBlank() }
            .distinct()
            .toTypedArray()

        val loader = Thread.currentThread().contextClassLoader
        ClassGraph()
            .enableClassInfo()
            .enableAnnotationInfo()
            .acceptPackages(*packages)
            .ignoreClassVisibility()
            .enableClassLoaders(loader)
            .scan()
            .use { scan ->
                val classes = scan.getAllSubclasses(FEATURE_FQN)
                Log.main.debug(
                    "ClassGraph found {} Feature subclasses in {}",
                    classes.size, packages.joinToString(),
                )

                classes.forEach { info ->
                    if (!shouldRegisterFeature(info, DevFlags.isDevelopment)) return@forEach
                    val instance = loadObjectInstance<Feature>(info, loader, "Feature") ?: return@forEach
                    try {
                        // Resolved now: an undeclared category throws, and startup beats the first ClickGUI open for that.
                        instance.effectiveSection
                        instance.initialize()
                        val addonId = addonIdForPackage(info.packageName, accepted)
                        registrationValidator.rejectionFor(instance, addonId)?.let { reason ->
                            Log.main.error("Refusing feature {} - {}", info.name, reason)
                            return@forEach
                        }
                        features.add(instance)
                        addonId?.let { addonIds[instance] = it }
                        instance.hudElements.forEach { hud ->
                            hudElements += hud
                            addonId?.let { hudAddonIds[hud] = it }
                        }
                        Log.main.debug("Registered feature '{}'", instance.id)
                    } catch (t: Throwable) {
                        Log.main.error("Failed to initialize feature ${info.name}", t)
                    }
                }

                // After all features, because extensions need their host to exist with its settings declared.
                scan.getAllSubclasses(EXTENSION_FQN).forEach { info ->
                    if (!shouldRegisterFeature(info, DevFlags.isDevelopment)) return@forEach
                    val instance = loadObjectInstance<FeatureExtension>(info, loader, "Feature extension")
                        ?: return@forEach
                    // A refused host never activates, so its extensions do nothing.
                    if (instance.host !in features) {
                        Log.main.error("Refusing feature extension {} - its host '{}' is not registered", info.name, instance.host.id)
                        return@forEach
                    }
                    val hostListeners = instance.host.listeners.toSet()
                    try {
                        instance.initialize()
                        registrationValidator.rejectionFor(instance)?.let { reason ->
                            Log.main.error("Refusing feature extension {} - {}", info.name, reason)
                            withdraw(instance, hostListeners)
                            return@forEach
                        }
                        extensions.getOrPut(instance.host, ::mutableListOf) += instance
                        Log.main.debug("Registered extension '{}' on feature '{}'", instance.id, instance.host.id)
                    } catch (t: Throwable) {
                        Log.main.error("Failed to initialize feature extension ${info.name}", t)
                        withdraw(instance, hostListeners)
                    }
                }

                // No feature turns a step type off: saved steps need it for as long as the game runs.
                scan.getAllSubclasses(STEP_TYPE_FQN).forEach { info ->
                    if (info.isAbstract || !shouldRegisterFeature(info, DevFlags.isDevelopment)) return@forEach
                    val type = loadObjectInstance<StepType<*>>(info, loader, "Action step type") ?: return@forEach
                    StepTypes.register(type)?.let { reason -> Log.main.error("Refusing action step type {} - {}", info.name, reason) }
                }
                scan.getAllSubclasses(STEP_FLAG_FQN).forEach { info ->
                    if (info.isAbstract || !shouldRegisterFeature(info, DevFlags.isDevelopment)) return@forEach
                    val flag = loadObjectInstance<StepFlag>(info, loader, "Action step flag") ?: return@forEach
                    StepTypes.register(flag)?.let { reason -> Log.main.error("Refusing action step flag {} - {}", info.name, reason) }
                }
            }

        pendingAddons = acceptedRegistrations
        loadedAddons = accepted.map { it.id }
        FeatureToggleKeys.init()
    }

    private var pendingAddons: List<AddonRegistration> = emptyList()

    private val addonModIds = IdentityHashMap<Addon, String>()

    /** The loader's mod id for a registered [addon]. It decides which name the addon logs under. */
    internal fun modIdOf(addon: Addon): String? = synchronized(addonModIds) { addonModIds[addon] }

    /** Once, after the config is read and before [activate]. */
    fun loadAddons() {
        val registrations = pendingAddons
        pendingAddons = emptyList()
        registrations.forEach { registration ->
            val addon = registration.addon
            try {
                addon.onLoad(HostAddonContext(registration.modId))
                Log.main.debug("Loaded addon '{}'", addon.id)
            } catch (t: Throwable) {
                Log.main.error("Addon '${addon.id}' onLoad threw", t)
            }
        }
    }

    /** Syncs every feature with the config. Separate from [init], because discovery runs before the config. Idempotent. */
    fun activate() {
        for (feature in features) {
            try {
                feature.activate()
            } catch (t: Throwable) {
                Log.main.error("Failed to activate feature ${feature.id}", t)
            }
        }
    }

    /** Before any feature is constructed, so package segments resolve against addon tabs. */
    private fun registerAddonCategories(addons: List<Addon>) {
        for (addon in addons) {
            val categories = try {
                addon.categories
            } catch (t: Throwable) {
                Log.main.error("Addon '${addon.id}' threw while declaring its categories", t)
                continue
            }
            CategoryRegistry.register(addon.id, categories).forEach { refused ->
                Log.main.error(
                    "Addon '{}' declares category '{}', which is already taken - its features should " +
                            "go in the existing one instead of declaring a second",
                    addon.id, refused.id,
                )
            }
        }
    }

    private fun validateAddons(registrations: List<AddonRegistration>): List<AddonRegistration> {
        val seen = mutableSetOf<String>()
        val accepted = mutableListOf<AddonRegistration>()
        for (registration in registrations) {
            val addon = registration.addon
            val id = try {
                addon.id
            } catch (t: Throwable) {
                Log.main.error("Addon ${addon::class.java.name} threw while reporting its id - refusing to load", t)
                continue
            }
            when {
                id.isBlank() -> Log.main.error(
                    "Addon ${addon::class.java.name} declares a blank id - refusing to load",
                )
                id in seen -> Log.main.error(
                    "Addon ${addon::class.java.name} reuses id '{}' already claimed by an earlier addon - refusing to load",
                    id,
                )
                else -> {
                    // Not fatal, but the config goes under the declared id.
                    if (id != registration.modId) Log.main.warn(
                        "Addon '{}' declares an id that isn't its mod id '{}' - its config is stored under the declared one",
                        id, registration.modId,
                    )
                    seen += id
                    accepted += registration
                }
            }
        }
        return accepted
    }

    // Undoes a refused extension's settings and listeners. Otherwise a duplicate setting id swaps values with the host.
    private fun withdraw(extension: FeatureExtension, hostListeners: Set<EventListener<*>>) {
        extension.host.configSettings.removeAll(extension.configSettings)
        extension.host.listeners.retainAll(hostListeners)
    }

    private fun addonIdForPackage(featurePackage: String, addons: List<Addon>): String? =
        addons.firstOrNull { addon ->
            val pkg = addon::class.java.packageName
            featurePackage == pkg || featurePackage.startsWith("$pkg.")
        }?.id

    fun byId(id: String): Feature? = features.firstOrNull { it.id == id }

    fun extensionsOf(host: Feature): List<FeatureExtension> = extensions[host].orEmpty()
    fun byName(name: String): Feature? = features.firstOrNull { it.name == name }
    fun byCategory(category: FeatureCategory): List<Feature> =
        features.filter { it.category == category }

    fun addonIdOf(hud: HudElement): String? = hudAddonIds[hud]

    /** Exact (id, addon) first. For a null [addonId] (legacy configs), a unique match on the bare id. */
    fun featureById(id: String, addonId: String?): Feature? {
        features.firstOrNull { it.id == id && addonIdOf(it) == addonId }?.let { return it }
        if (addonId != null) return null
        return features.filter { it.id == id }.singleOrNull()
    }

    fun hudByName(name: String, addonId: String?): HudElement? {
        hudElements.firstOrNull {
            it.name == name && addonIdOf(it) == addonId
        }?.let { return it }
        if (addonId != null) return null
        return hudElements.filter { it.name == name }.singleOrNull()
    }

    private inline fun <reified T : Any> loadObjectInstance(info: ClassInfo, loader: ClassLoader, what: String): T? {
        val instance = try {
            Class.forName(info.name, false, loader).getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null) as? T
        } catch (_: NoSuchFieldException) {
            null
        } catch (t: Throwable) {
            Log.main.error("Failed to load INSTANCE for ${info.name}", t)
            return null
        }
        if (instance != null) return instance

        val msg = "$what ${info.name} is not a Kotlin `object` (no INSTANCE field) - declare it as `object`, not `class`"
        if (DevFlags.isDevelopment) error(msg)
        Log.main.warn(msg)
        return null
    }
}

internal fun shouldRegisterFeature(info: ClassInfo, development: Boolean): Boolean =
    development || !info.hasAnnotation(DevOnly::class.java.name)
