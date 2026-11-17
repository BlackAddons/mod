package org.blackaddons.platform.event.impl

import net.minecraft.client.gui.screens.Screen
import org.blackaddons.api.event.Event

/**
 * After a vanilla [Screen] extracts, once per frame: for drawing over screens we don't own. Unlike
 * [RenderOverlayEvent], it's after BlackSkija's HUD split, so it draws above screens and chat. [screen] is the one that
 * just drew.
 */
internal class RenderScreenEvent(val screen: Screen, val tickDelta: Float) : Event()
