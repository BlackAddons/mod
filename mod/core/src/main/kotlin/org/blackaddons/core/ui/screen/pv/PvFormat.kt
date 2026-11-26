package org.blackaddons.core.ui.screen.pv

import org.blackaddons.api.skyblock.profile.Networth
import org.blackaddons.api.skyblock.profile.SkillStat
import org.blackaddons.api.util.*
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val tr = I18n.scope("blackaddons.pv")

internal object PvFormat {

    private val JOIN_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    /** "2.14B" / "412.5M" / "67.1K". Grouped digits below 1000. */
    fun compact(n: Double): String = when {
        n >= 1_000_000_000 -> "%.2fB".format(Locale.ROOT, n / 1_000_000_000)
        n >= 1_000_000 -> "%.1fM".format(Locale.ROOT, n / 1_000_000)
        n >= 1_000 -> "%.1fK".format(Locale.ROOT, n / 1_000)
        else -> "%,.0f".format(Locale.ROOT, n)
    }

    fun exact(n: Double): String = n.formatGrouped()

    /** Two decimals. No overflow level at the cap. */
    fun skillLevel(skill: SkillStat): String = skillLevelValue(skill).formatDecimals()

    fun slayerName(name: String): String = localizedName("slayer", name)

    fun dungeonClassName(name: String): String = localizedName("dungeon_class", name)

    fun skillLevelValue(skill: SkillStat): Double = skill.level + if (skill.level < skill.cap) skill.progress else 0.0

    fun prettyCategory(key: String): String =
        key.split('_').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

    fun networthCategory(key: String): String =
        tr("networth_category.${I18n.slug(key)}", prettyCategory(key))

    /** Networth hover: total, then non-zero categories, high to low. */
    fun networthTooltip(nw: Networth, label: String): String = buildString {
        append(label).append(": ").append(compact(nw.total))
        nw.categories.entries.filter { it.value > 0 }.sortedByDescending { it.value }
            .forEach { (name, value) -> append('\n').append(networthCategory(name)).append(": ").append(compact(value)) }
        if (nw.bank > 0) append('\n').append(tr("bank", "Bank")).append(": ").append(compact(nw.bank))
        if (nw.unavailable.isNotEmpty()) {
            append('\n').append(tr("hidden", "Hidden (API off)")).append(": ")
            append(nw.unavailable.joinToString(", ") { networthCategory(it) })
        }
    }

    fun networthCategoryTooltip(label: String, value: Double, categoryTotal: Double): String {
        val share = if (categoryTotal > 0.0) value / categoryTotal * 100.0 else 0.0
        return tr("networth_detail.category_tooltip", "%s: %s\n%.1f%% of valued categories")
            .format(label, exact(value), share)
    }

    fun joinTooltip(epochMillis: Long): String? {
        if (epochMillis <= 0L) return null
        val date = JOIN_DATE.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
        return tr("first_join", "First Join") + ": " + date
    }

    fun profileAge(epochMillis: Long): String? {
        if (epochMillis <= 0L) return null
        val joined = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val age = Period.between(joined, LocalDate.now())
        val years = tr("years", "%dy").format(age.years)
        val months = tr("months", "%dmo").format(age.months)
        return if (age.years > 0) "$years $months" else months
    }

    fun axis(v: Float): String = v.formatTrimmed()

    /** Slayer chip hover: kills per tier, then total. Tiers without kills are left out. Null with no kills. */
    fun slayerKillsTooltip(kills: List<Int>, xp: Double): String? {
        val total = kills.sum()
        if (total == 0) return null
        return buildString {
            kills.forEachIndexed { i, n ->
                if (n > 0) {
                    if (isNotEmpty()) append('\n')
                    append(tr("tier", "Tier %s").format(slayerTier(i)))
                    append(": ").append(n.formatGrouped())
                }
            }
            append('\n')
            append('\n').append(tr("total", "Total")).append(": ").append(total.formatGrouped())
            append('\n').append(tr("combat_detail.slayer_xp", "Slayer XP")).append(": ").append(xp.roundToInt().formatGrouped())
        }
    }

    /** Defined up to tier V. Extra entries show as numbers. */
    fun slayerTier(index: Int): String {
        val tier = index + 1
        return if (tier in 1..SLAYER_TIER_CAP) tier.toRoman() else tier.toString()
    }

    private fun localizedName(kind: String, name: String): String =
        tr("$kind.${I18n.slug(name)}", name)

    private const val SLAYER_TIER_CAP = 5
}
