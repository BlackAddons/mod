package org.blackaddons.core.update

import org.blackaddons.core.update.ModVersion.Companion.parse


/**
 * Mod version in semver order. A string compare would rank `1.4.10` below `1.4.9`, and the backend's `latest` is in
 * publish order, not version order. [parse] returns null for non-versions (dev builds, git hashes), and those never get
 * compared.
 */
internal class ModVersion private constructor(
    private val numbers: List<Int>,
    /** `1.1.0-beta.3` -> `["beta", "3"]`. */
    val prerelease: List<String>,
) : Comparable<ModVersion> {

    override fun compareTo(other: ModVersion): Int {
        // "1.4" is equal to "1.4.0".
        for (i in 0 until maxOf(numbers.size, other.numbers.size)) {
            val diff = numbers.getOrElse(i) { 0 }.compareTo(other.numbers.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return comparePrerelease(other)
    }

    private fun comparePrerelease(other: ModVersion): Int {
        if (prerelease.isEmpty() || other.prerelease.isEmpty()) {
            return other.prerelease.size.compareTo(prerelease.size)
        }
        for (i in 0 until maxOf(prerelease.size, other.prerelease.size)) {
            val mine = prerelease.getOrNull(i) ?: return -1
            val theirs = other.prerelease.getOrNull(i) ?: return 1
            val diff = compareIdentifier(mine, theirs)
            if (diff != 0) return diff
        }
        return 0
    }

    private fun compareIdentifier(a: String, b: String): Int {
        val na = a.toIntOrNull()
        val nb = b.toIntOrNull()
        return when {
            na != null && nb != null -> na.compareTo(nb)
            // Numeric identifiers rank below alphanumeric ones.
            na != null -> -1
            nb != null -> 1
            else -> a.compareTo(b)
        }
    }

    companion object {

        fun parse(text: String?): ModVersion? {
            val trimmed = text?.trim()?.removePrefix("v").orEmpty()
            if (trimmed.isEmpty()) return null

            val withoutBuild = trimmed.substringBefore('+')
            // Found with indexOf, not split, so a trailing "-" gets rejected instead of parsing as the release.
            val dash = withoutBuild.indexOf('-')
            val core = if (dash < 0) withoutBuild else withoutBuild.substring(0, dash)

            val numbers = core.split('.').map { it.toIntOrNull() ?: return null }
            if (numbers.any { it < 0 }) return null

            val identifiers = if (dash < 0) emptyList() else withoutBuild.substring(dash + 1).split('.')
            if (identifiers.any { it.isEmpty() }) return null

            return ModVersion(numbers, identifiers)
        }
    }
}
