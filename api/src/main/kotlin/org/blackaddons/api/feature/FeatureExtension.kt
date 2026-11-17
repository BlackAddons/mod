package org.blackaddons.api.feature

import org.blackaddons.api.event.Event
import org.blackaddons.api.event.EventBus
import org.blackaddons.api.event.EventListener
import org.blackaddons.api.event.EventPriority
import org.blackaddons.api.feature.settings.Setting
import org.blackaddons.api.feature.settings.SettingProvider
import org.blackaddons.api.feature.settings.impl.KeybindSetting

/**
 * Settings and listeners one module adds to another module's [Feature] (e.g. cheater mode on the
 * Dungeon Map). They show in the host's row and live exactly as long as the host is enabled.
 * Discovered like features, so never registered by hand. No toggle of its own - declare a `boolean`.
 *
 * ```
 * object DungeonMapCheats : FeatureExtension(DungeonMap, group = "Cheats") {
 *     val cheaterMap by boolean("Cheater Map", false)
 *
 *     override fun init() {
 *         register<RenderWorldEvent> { ... }
 *     }
 * }
 * ```
 */
abstract class FeatureExtension(
    /** Its enabled state is this extension's. */
    val host: Feature,
    private val group: String? = null,
) : SettingProvider, MenuActionProvider {

    /** Settings persistence and lang namespace, so an addon translates its own contributions. */
    override val id: String = this::class.simpleName!!

    /** Shown separately from the host's labels; see [FeatureLabel]. */
    open val labels: List<FeatureLabel> = emptyList()

    /** In declared order. The host holds the same settings. */
    val configSettings = linkedSetOf<Setting<*>>()

    /** Actions declared here show in the host's row, keyed under this extension. */
    override val menuActions: MutableList<MenuAction> get() = host.menuActions

    /** Called once by the feature manager. */
    fun initialize() {
        init()
    }

    /** Called before the host is first enabled. */
    open fun init() {}

    /** Active while the host is enabled; the listener joins the host's set. */
    inline fun <reified T : Event> register(
        priority: EventPriority = EventPriority.NORMAL,
        noinline block: EventBus.EventContext<T>.() -> Unit,
    ): EventListener<T> = host.register(priority, block)

    /** [Feature.onKeybind] on the host. */
    fun onKeybind(
        keybind: KeybindSetting,
        predicate: () -> Boolean = { host.isRunning },
        allowInScreens: Boolean = false,
        action: () -> Unit,
    ) = host.onKeybind(keybind, predicate, allowInScreens, action)

    override fun registerSetting(setting: Setting<*>) {
        if (setting.group == null) setting.group = group
        setting.namespace = id
        host.registerSetting(setting)
        configSettings += setting
    }
}
