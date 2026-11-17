package org.blackaddons.api.feature.annotations

/** Always enabled regardless of the toggle; listeners stay registered. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AlwaysActive
