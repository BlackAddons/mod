package org.blackaddons.api

/**
 * Marks host wiring: calls that bind a facade or set state that every addon reads. An addon that calls them breaks
 * the host for everyone, so using them is a compile error unless the module opts in. Only BlackAddons' own modules
 * opt in.
 */
@RequiresOptIn(
    message = "Host wiring: addons read this, only the BlackAddons host writes it.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_SETTER)
annotation class HostApi
