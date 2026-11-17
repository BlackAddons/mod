package org.blackaddons.api.feature.settings

import org.blackaddons.api.feature.Feature
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Base for feature settings. [id] is the persistence key (defaults to [name]); implement
 * [org.blackaddons.api.config.Savable] to persist.
 */
abstract class Setting<T>(
    val id: String,
    val name: String,
    val defaultValue: T,
) {
    /** The declaring feature, set when `by` registers it. */
    var owner: Feature? = null
        internal set

    /** Lang namespace: the declarer's id (a [org.blackaddons.api.feature.FeatureExtension]'s own, not its host's). */
    var namespace: String? = null
        internal set

    /** Every write goes through [normalize]. `onChange` listeners run only when the value actually changes. */
    open var value: T = defaultValue
        set(v) {
            val next = normalize(v)
            if (next == field) return
            field = next
            changeListeners.forEach { it(next) }
        }

    private val changeListeners = CopyOnWriteArrayList<(T) -> Unit>()

    internal fun addChangeListener(listener: (T) -> Unit) {
        changeListeners += listener
    }

    /** What a write stores, after clamping, snapping or validation. Returning the current value means no change. */
    protected open fun normalize(value: T): T = value

    var description: String? = null
    var visibility: () -> Boolean = { true }

    /** Persisted but never shown or searchable. Set via `hidden()`. */
    var isHidden: Boolean = false
        internal set

    val isVisible: Boolean get() = !isHidden && visibility()

    /**
     * Settings with the same [group] render in one collapsible subsection. Set via [SettingProvider.group].
     */
    var group: String? = null

    /** Badge shown while the value is risky ("UNSAFE", "BANNABLE"). Set via `unsafeWhen` / `safeRange`. */
    var unsafeLabel: String? = null
        internal set

    /** Hover text for [unsafeLabel], localized by the UI. The badge itself isn't localized. */
    var unsafeReason: String? = null
        internal set

    internal var unsafeCheck: ((T) -> Boolean)? = null

    val isUnsafe: Boolean get() = unsafeCheck?.invoke(value) == true

    open fun reset() { value = defaultValue }

    open fun onRegister() {}
}
