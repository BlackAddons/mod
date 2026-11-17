// Copyright (c) 2026 BlackAddons Team. All rights reserved.
// Licensed under the PolyForm Strict License 1.0.0 with additional permissions - see LICENSE.md and
// PERMISSIONS.md. Copying this code into other projects requires the BlackAddons Team's written permission.

package org.blackaddons.cheat

import org.blackaddons.api.Addon
import org.blackaddons.api.AddonContext
import org.blackaddons.cheat.actions.CheatActionStepHandler

/** Cheat-edition entrypoint for every platform jar. Features get found by package scan, we don't list them anywhere. */
object CheatAddon : Addon {
    override val id: String = "blackaddons-cheat"

    override fun onLoad(context: AddonContext) {
        CheatActionStepHandler.init()
    }
}

