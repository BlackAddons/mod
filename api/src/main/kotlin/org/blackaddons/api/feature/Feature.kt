package org.blackaddons.api.feature

import org.blackaddons.api.Platform
import org.blackaddons.api.config.Savable
import org.blackaddons.api.event.*
import org.blackaddons.api.event.input.KeyInputEvent
import org.blackaddons.api.event.input.MouseClickEvent
import org.blackaddons.api.feature.annotations.AlwaysActive
import org.blackaddons.api.feature.annotations.Dev
import org.blackaddons.api.feature.settings.Setting
import org.blackaddons.api.feature.settings.SettingProvider
import org.blackaddons.api.feature.settings.impl.BooleanSetting
import org.blackaddons.api.feature.settings.impl.KeybindSetting
import org.blackaddons.api.hud.AnchorX
import org.blackaddons.api.hud.AnchorY
import org.blackaddons.api.hud.HudElement
import org.blackaddons.api.input.KeyToggle
import org.blackaddons.api.log.Log
import org.blackaddons.api.ui.layout.LayoutNode
import org.blackaddons.api.ui.theme.FontChoice
import org.blackaddons.api.util.spaceCaps

/**
 * Base for every feature; must be an `object`, discovered at startup.
 * [id] defaults to the simple class name - override it if the class may be renamed.
 * The category comes from the package (`features.impl.<category>.X`), [section] or [Dev].
 *
 * ```
 * override fun init() {
 *     register<ClientTickEvent.Post> { /* ... */ }
 * }
 * ```
 */
open class Feature(
    val description: String? = null,
    name: String? = null,
    toggled: Boolean = false,
) : SettingProvider, MenuActionProvider {
    override val id: String = this::class.simpleName!!
    val name: String = name ?: this::class.simpleName!!.spaceCaps()

    val listeners = mutableSetOf<EventListener<*>>()
    val configSettings = mutableSetOf<Setting<*>>()
    override val menuActions = mutableListOf<MenuAction>()
    val hudElements = mutableSetOf<HudElement>()

    @JvmField
    var enabled: Boolean = toggled

    /** Flips [enabled] in a world. Set from the feature's context menu; not a setting, so reset and export skip it. */
    val toggleBind = KeybindSetting("Toggle Bind")

    private val isDev = this::class.java.isAnnotationPresent(Dev::class.java)
    private val alwaysActive = this::class.java.isAnnotationPresent(AlwaysActive::class.java)

    /** UI hides the toggle for [AlwaysActive] features. */
    val isAlwaysActive: Boolean get() = alwaysActive

    /** What keybinds, HUD and control requests should check instead of [enabled]. */
    val isRunning: Boolean get() = enabled || alwaysActive

    /**
     * Subcategory, which also decides [category]:
     * ```
     * override val section = Categories.Dungeons.Floor7
     * ```
     */
    open val section: FeatureSection? = null

    /** ClickGUI warnings; see [FeatureLabel]. */
    open val labels: List<FeatureLabel> = emptyList()

    /** A getter on purpose: a subclass's [section] override is initialized after this constructor runs. */
    open val category: FeatureCategory
        get() = section?.category ?: inferredCategory

    val effectiveSection: FeatureSection? get() = section ?: category.defaultSection

    private val inferredCategory: FeatureCategory by lazy {
        if (isDev) Categories.Dev else CategoryRegistry.infer(this::class.java)
    }

    private var active = false

    /** Called once by the feature manager. */
    fun initialize() {
        init()
    }

    /**
     * Syncs [onEnable]/[onDisable] with [enabled]. Not part of [initialize] because the config isn't read yet
     * at discovery; the manager calls it after every config read.
     */
    fun activate() {
        val shouldRun = enabled || alwaysActive
        if (shouldRun == active) return
        active = shouldRun
        if (shouldRun) {
            listeners.forEach(EventListener<*>::register)
            try {
                onEnable()
            } catch (t: Throwable) {
                /*
                 * Left off so a retry runs onEnable again. Caught here because a ClickGUI toggle has no catch
                 * and Minecraft would crash-report it.
                 */
                Log.main.error("Feature '$id' threw in onEnable - leaving it off", t)
                listeners.forEach(EventListener<*>::unregister)
                active = false
                enabled = false
            }
        } else {
            listeners.forEach(EventListener<*>::unregister)
            try {
                onDisable()
            } catch (t: Throwable) {
                Log.main.error("Feature '$id' threw in onDisable", t)
            }
        }
    }

    /** Register listeners and HUD elements here. Called before the first enable. */
    open fun init() {}

    /** Listeners are registered before this runs. */
    open fun onEnable() {}

    /** Listeners are unregistered before this runs. */
    open fun onDisable() {}

    open fun toggle() {
        if (alwaysActive) return
        enabled = !enabled
        activate()
    }

    /** True if there's a saved setting or [OwnState] to reset. */
    val isResettable: Boolean get() = this is OwnState || configSettings.any { it is Savable }

    /** Resets settings and [OwnState]; the enabled state is kept. */
    fun resetSettings() {
        configSettings.forEach(Setting<*>::reset)
        (this as? OwnState)?.resetOwnState()
    }

    inline fun <reified T : Event> register(
        priority: EventPriority = EventPriority.NORMAL,
        noinline block: EventBus.EventContext<T>.() -> Unit,
    ): EventListener<T> {
        val listener = EventListener(T::class.java, priority, block)
        listeners.add(listener)
        return listener
    }

    /**
     * Runs [action] on press of [keybind] (key or mouse). Ignores repeats, unbound keys, open screens (unless
     * [allowInScreens]) and [predicate] rejections.
     */
    fun onKeybind(
        keybind: KeybindSetting,
        predicate: () -> Boolean = { isRunning },
        allowInScreens: Boolean = false,
        action: () -> Unit,
    ) {
        fun allowed() = predicate() && (allowInScreens || !Platform.screenHost.anyScreenOpen)

        register<KeyInputEvent> {
            if (!event.isRepeat && keybind.matches(event) && allowed()) action()
        }
        register<MouseClickEvent> {
            if (keybind.matches(event) && allowed()) action()
        }
    }

    /**
     * Toggles this feature with [key] while in a world, even while the feature is off. With [holdMode] on, it
     * stays on only while the key is held, and turns off when a screen opens. Call it once, from [init].
     */
    protected fun toggleKey(key: KeybindSetting, holdMode: BooleanSetting) {
        val toggle = KeyToggle { holdMode.value }
        // Not `register`: that listener would go away with the feature it has to turn back on.
        EventBus.subscribe<ClientTickEvent.Post> {
            toggle.active = enabled
            val readable = key.isBound && Platform.player.isInGame && !Platform.screenHost.anyScreenOpen
            if (readable) toggle.update(key.isPressed) else toggle.release()
            if (toggle.active != enabled) toggle()
        }
    }

    /** HUD element drawn by the [content] layout tree; see [HudElement.content] for what's allowed while building. */
    protected fun hudElement(
        name: String,
        enabled: () -> Boolean = { true },
        shouldDraw: () -> Boolean = { true },
        resizable: Boolean = true,
        hasText: Boolean = true,
        defaultAnchorX: AnchorX = AnchorX.LEFT,
        defaultAnchorY: AnchorY = AnchorY.TOP,
        defaultOffsetX: Float = 0f,
        defaultOffsetY: Float = 0f,
        defaultScale: Float = 1f,
        defaultFont: FontChoice = FontChoice.INHERIT,
        content: (Boolean) -> LayoutNode,
    ): HudElement {
        val capturedDefaultAnchorX = defaultAnchorX
        val capturedDefaultAnchorY = defaultAnchorY
        val capturedDefaultOffsetX = defaultOffsetX
        val capturedDefaultOffsetY = defaultOffsetY
        val capturedDefaultScale = defaultScale
        val capturedHasText = hasText
        val element = object : HudElement() {
            override val name = name
            override val toggle: Boolean get() = isRunning && enabled()
            override val shouldDraw: Boolean get() = shouldDraw()
            override val resizable = resizable
            override val hasText = capturedHasText
            override val defaultAnchorX: AnchorX get() = capturedDefaultAnchorX
            override val defaultAnchorY: AnchorY get() = capturedDefaultAnchorY
            override val defaultOffsetX: Float get() = capturedDefaultOffsetX
            override val defaultOffsetY: Float get() = capturedDefaultOffsetY
            override val defaultScale: Float get() = capturedDefaultScale
            override val defaultFontOverride: FontChoice get() = defaultFont
            override fun content(example: Boolean) = content(example)
        }
        element.resetToDefault()
        hudElements.add(element)
        return element
    }

    /** Its key namespace stays with whoever declared it. */
    override fun registerSetting(setting: Setting<*>) {
        setting.owner = this
        if (setting.namespace == null) setting.namespace = id
        configSettings.add(setting)
        setting.onRegister()
    }

    fun getSettingById(id: String): Setting<*>? = configSettings.firstOrNull { it.id == id }
}
