package org.blackaddons.cheat.actions

import org.blackaddons.api.feature.FeatureExtension
import org.blackaddons.core.features.impl.settings.Actions

/** Kept out of [Actions], legit builds shouldn't show dead controls. Ids are the same, so old configs still load. */
internal object AlignSettings : FeatureExtension(Actions, group = "Experimental") {

    private val disableSilentAlign by boolean(name = "Disable Silent Align", default = false)
        .withDescription("Turn off silent alignment during ActionStep ALIGN execution, restoring visual camera snapping and movement input locks.")

    private val alignDebugger by boolean(name = "Align Debugger", default = false)
        .withDescription("Enable the 3D visual debugger for alignment (rendering target offset and predicted trajectories in the world).")

    private val alignTimeout by number(name = "Align Timeout", default = 5.0, min = 0.5, max = 20.0, step = 0.1, unit = "s")
        .withDescription("Default timeout in seconds for ALIGN action step when not specified by the step.")

    internal val dontWaitForPerfectAlign by boolean(name = "Don't Wait for Perfect Align by Default", default = true)
        .withDescription("Default for new ALIGN steps. Finish once movement is predicted to stop within 12 mm instead of waiting for horizontal velocity to reach zero.")

    private val minimizeYawSnap by boolean(name = "Align Minimize Yaw Snap", default = true)
        .withDescription("Use the equivalent cardinal movement input that keeps server yaw within 45° of its previous value. Aka 'rotates less to get best result'. Should be around same accuracy.")

    private val smoothAlignYawReset by boolean(name = "Smooth Align Yaw Reset", default = false)
        .withDescription("Smoothly return server look to camera rotation after silent align. Movement is briefly blocked so client and server physics stay synchronized.")

    private val alignYawResetSpeed by number(name = "Align Yaw Reset Speed", default = 30.0, min = 5.0, max = 90.0, step = 1.0, unit = "°/t")
        .withDescription("Maximum yaw and pitch change per tick when returning to the camera rotation after silent align.")
        .showIf { smoothAlignYawReset.value }

    val isSilentAlignDisabled: Boolean get() = disableSilentAlign.value

    val isDebuggerEnabled: Boolean get() = alignDebugger.value

    /** For ALIGN steps that don't have their own duration. */
    val timeoutMs: Long get() = (alignTimeout.value * 1000.0).toLong()

    val mayFinishWhileMoving: Boolean get() = dontWaitForPerfectAlign.value

    fun shouldFinishWhileMoving(stepValue: Boolean?): Boolean = stepValue == true

    val isSmoothAlignYawResetEnabled: Boolean get() = smoothAlignYawReset.value

    val yawResetSpeed: Float get() = alignYawResetSpeed.value.toFloat()

    val isMinimizeYawSnapEnabled: Boolean get() = minimizeYawSnap.value
}
