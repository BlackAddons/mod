package org.blackaddons.api.util

import java.text.BreakIterator
import java.util.Locale
import kotlin.math.roundToInt

private val SPACE_CAPS = Regex("([a-z0-9])([A-Z])")

val WHITESPACE_REGEX = Regex("\\s+")

/** Any char after `§`, so the `§x§r§g§b` hex form is fully stripped too. */
private val FORMATTING_CODE = Regex("§.")

fun String.spaceCaps(): String =
    replace(SPACE_CAPS, "$1 $2").replace('_', ' ')

fun String.stripFormatting(): String = replace(FORMATTING_CODE, "")

fun String.cleanName(): String = stripFormatting().trim().lowercase()

fun String.cleanCommand(): String = trim().removePrefix("/")

fun CharSequence.containsIgnoreCase(other: CharSequence): Boolean =
    contains(other, ignoreCase = true)

fun String.startsWithIgnoreCase(prefix: String): Boolean =
    startsWith(prefix, ignoreCase = true)

fun String?.equalsIgnoreCase(other: String?): Boolean =
    this.equals(other, ignoreCase = true)

/** Replaces unpaired surrogates with U+FFFD. Returns `this` without allocating when there are none. */
fun String.sanitizeUtf16(): String {
    val len = length
    var hasUnpaired = false
    var i = 0
    while (i < len) {
        val c = this[i]
        if (Character.isHighSurrogate(c)) {
            if (i + 1 < len && Character.isLowSurrogate(this[i + 1])) {
                i += 2
                continue
            }
            hasUnpaired = true
            break
        } else if (Character.isLowSurrogate(c)) {
            hasUnpaired = true
            break
        }
        i++
    }
    if (!hasUnpaired) return this

    val sb = StringBuilder(len)
    i = 0
    while (i < len) {
        val c = this[i]
        if (Character.isHighSurrogate(c)) {
            if (i + 1 < len && Character.isLowSurrogate(this[i + 1])) {
                sb.append(c)
                sb.append(this[i + 1])
                i += 2
            } else {
                sb.append('\uFFFD')
                i++
            }
        } else if (Character.isLowSurrogate(c)) {
            sb.append('\uFFFD')
            i++
        } else {
            sb.append(c)
            i++
        }
    }
    return sb.toString()
}

/** At most [n] UTF-16 units, never splitting a surrogate pair. */
fun String.takeValidUtf16(n: Int): String {
    if (n <= 0) return ""
    if (n >= length) return this
    val cut = if (Character.isHighSurrogate(this[n - 1])) n - 1 else n
    return substring(0, cut)
}

/** Extended grapheme clusters; unpaired surrogates are sanitized first. */
fun String.graphemes(): List<String> {
    if (isEmpty()) return emptyList()
    val sanitized = sanitizeUtf16()
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(sanitized)
    val result = ArrayList<String>()
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        result += sanitized.substring(start, end)
        start = end
        end = iterator.next()
    }
    return result
}

fun String.truncate(maxLength: Int, ellipsis: String = "..."): String {
    if (length <= maxLength) return this
    val cutTarget = (maxLength - ellipsis.length).coerceAtLeast(0)
    return takeValidUtf16(cutTarget) + ellipsis
}

fun CharSequence.splitWhitespace(): List<String> =
    split(WHITESPACE_REGEX).filter { it.isNotEmpty() }

fun String.colorize(altColorChar: Char = '&'): String = replace(altColorChar, '§')

private val ROMAN_NUMERALS = arrayOf(
    1000 to "M", 900 to "CM", 500 to "D", 400 to "CD",
    100 to "C", 90 to "XC", 50 to "L", 40 to "XL",
    10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I",
)

private val ROMAN_VALUES = mapOf(
    'I' to 1, 'V' to 5, 'X' to 10, 'L' to 50,
    'C' to 100, 'D' to 500, 'M' to 1000,
)

/** Non-positive values are returned as plain numbers. */
fun Int.toRoman(): String {
    if (this <= 0) return this.toString()
    var num = this
    val sb = StringBuilder()
    for ((value, numeral) in ROMAN_NUMERALS) {
        while (num >= value) {
            sb.append(numeral)
            num -= value
        }
    }
    return sb.toString()
}

/** Case-insensitive; null if invalid. */
fun String.parseRomanOrNull(): Int? {
    val clean = trim().uppercase()
    if (clean.isEmpty() || clean.any { it !in ROMAN_VALUES }) return null
    var total = 0
    var prev = 0
    for (i in clean.length - 1 downTo 0) {
        val current = ROMAN_VALUES[clean[i]] ?: return null
        if (current < prev) {
            total -= current
        } else {
            total += current
            prev = current
        }
    }
    return if (total > 0) total else null
}

/** Root locale. */
fun Double.formatDecimals(decimals: Int = 2): String =
    "%.${decimals}f".format(Locale.ROOT, this)

/** Root locale. */
fun Float.formatDecimals(decimals: Int = 2): String =
    "%.${decimals}f".format(Locale.ROOT, this)

/** No trailing zeros ("1.5", "2"). Rounds first, so 0.9975 -> "1" and no "-0". */
fun Double.formatTrimmed(decimals: Int = 2): String {
    val fixed = formatDecimals(decimals)
    val trimmed = if ('.' in fixed) fixed.trimEnd('0').trimEnd('.') else fixed
    return if (trimmed == "-0") "0" else trimmed
}

fun Float.formatTrimmed(decimals: Int = 2): String = toDouble().formatTrimmed(decimals)

/** A 0..1 share as a rounded percentage: 0.456 -> "46%". */
fun Double.formatPercent(): String = "${(this * 100).roundToInt()}%"

fun Float.formatPercent(): String = toDouble().formatPercent()

/** "x, y, z", root locale. */
fun formatCoordinates(x: Double, y: Double, z: Double, decimals: Int = 2): String =
    "${x.formatDecimals(decimals)}, ${y.formatDecimals(decimals)}, ${z.formatDecimals(decimals)}"

/** Root locale: 1234567 -> "1,234,567". */
fun Int.formatGrouped(): String = "%,d".format(Locale.ROOT, this)

fun Long.formatGrouped(): String = "%,d".format(Locale.ROOT, this)

/** Rounded: 1234.5 -> "1,235". */
fun Double.formatGrouped(): String = "%,.0f".format(Locale.ROOT, this)
