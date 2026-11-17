// Copyright (c) 2026 BlackAddons Team. All rights reserved.
// Licensed under the PolyForm Strict License 1.0.0 with additional permissions - see LICENSE.md and
// PERMISSIONS.md. Copying this code into other projects requires the BlackAddons Team's written permission.

package org.blackaddons.extras

import org.blackaddons.api.Addon
import org.blackaddons.api.feature.FeatureCategory
import org.blackaddons.api.ui.icon.LucideIcons

/**
 * Declared so a typo errors out instead of minting a one-feature tab. Flat until we have enough features for sections.
 */
object ExtrasCategory : FeatureCategory("extras", LucideIcons.extras)

/**
 * Extras addon entrypoint, shared by every platform jar. Some features are ported from SkyblockAddons (MIT, © Biscuit
 * Development); the Storage Overlay reimplements NEU's (GNU LGPLv3) behavior without its code.
 */
object ExtrasAddon : Addon {
    override val id: String = "blackaddons-extras"

    override val categories: List<FeatureCategory> = listOf(ExtrasCategory)
}
