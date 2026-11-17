package org.blackaddons.api.util

import java.util.Locale
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

const val TICKS_PER_SECOND = 20
const val MS_PER_TICK = 50L

/** Rounded. */
val Float.secondsToTicks: Int
    get() = (this.coerceAtLeast(0f) * TICKS_PER_SECOND).roundToInt()

/** Rounded. */
val Double.secondsToTicks: Int
    get() = (this.coerceAtLeast(0.0) * TICKS_PER_SECOND).roundToInt()

val Int.ticksToSeconds: Float
    get() = this / TICKS_PER_SECOND.toFloat()

val Int.ticksToDuration: Duration
    get() = (this * MS_PER_TICK).milliseconds

val Duration.inWholeTicks: Long
    get() = inWholeMilliseconds / MS_PER_TICK

/** `ss.SSS`, e.g. `02.300`. */
fun formatSecondsMillis(ms: Long): String {
    val clamped = ms.coerceAtLeast(0L)
    val seconds = clamped / 1000L
    val millis = clamped % 1000L
    return String.format(Locale.ROOT, "%02d.%03d", seconds, millis)
}

/** `mm:ss.SSS`, e.g. `01:05.123`. */
fun formatMinutesSecondsMillis(ms: Long): String {
    val clamped = ms.coerceAtLeast(0L)
    val totalSeconds = clamped / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    val millis = clamped % 1000L
    return String.format(Locale.ROOT, "%02d:%02d.%03d", minutes, seconds, millis)
}

/** `HH:mm:ss.SSS`. */
fun formatHoursMinutesSecondsMillis(ms: Long): String {
    val clamped = ms.coerceAtLeast(0L)
    val totalSeconds = clamped / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    val millis = clamped % 1000L
    return String.format(Locale.ROOT, "%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
}

/** `HH:mm:ss.SSS` with hours, else `mm:ss.SSS`. */
fun formatDurationWithMillis(ms: Long): String {
    val clamped = ms.coerceAtLeast(0L)
    return if (clamped >= 3600_000L) {
        formatHoursMinutesSecondsMillis(clamped)
    } else {
        formatMinutesSecondsMillis(clamped)
    }
}

fun formatTimeWithMinutes(ms: Long): String = formatMinutesSecondsMillis(ms)

/** Drops leading zero units: `HH:mm:ss.SSS`, `mm:ss.SSS` or `ss.SSS`. */
fun formatDesync(ms: Long): String {
    val clamped = ms.coerceAtLeast(0L)
    val totalSeconds = clamped / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    val millis = clamped % 1000L

    return when {
        hours > 0L -> String.format(Locale.ROOT, "%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
        minutes > 0L -> String.format(Locale.ROOT, "%02d:%02d.%03d", minutes, seconds, millis)
        else -> String.format(Locale.ROOT, "%02d.%03d", seconds, millis)
    }
}
