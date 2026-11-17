package org.blackaddons.platform

// A facade with a startup hook beyond binding. SharedPlatform.install runs them once, in fixed order.
interface Installable {
    fun install()
}
