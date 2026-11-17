@file:JvmName("ModIds")

package org.blackaddons.platform.util

import net.minecraft.resources.Identifier
import org.blackaddons.platform.Blackaddons

internal fun modId(path: String): Identifier = Identifier.fromNamespaceAndPath(Blackaddons.MOD_ID, path)
