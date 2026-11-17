package org.blackaddons.core.control

import org.blackaddons.api.feature.Feature
import org.blackaddons.api.feature.FeatureExtension

/** A disabled feature or extension. Its requests expire. */
internal fun isSwitchedOff(owner: Any?): Boolean = when (owner) {
    is Feature -> !owner.isRunning
    is FeatureExtension -> !owner.host.isRunning
    else -> false
}
