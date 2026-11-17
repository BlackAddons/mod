package org.blackaddons.core.actions.execution
import org.blackaddons.api.Platform

internal object ActionInterpolator {
    private const val PLAYER_TOKEN = "{player}"

    /**
     * Fills {player}, {args} and {0}, {1}, ... from [groups]. {player} is u when nobody else triggered it. {player}
     * goes first, so a captured "{player}" stays literal.
     */
    fun interpolate(
        template: String,
        groups: Array<String>?,
        player: String? = null,
    ): String {
        if (template.isEmpty()) return template

        var result = template
        if (result.contains(PLAYER_TOKEN)) {
            result = result.replace(PLAYER_TOKEN, player ?: Platform.player.name.orEmpty())
        }
        if (groups.isNullOrEmpty()) return result

        if (result.contains("{args}")) {
            result = result.replace("{args}", groups[0])
        }
        if (result.contains("{0}")) {
            result = result.replace("{0}", groups[0])
        }
        for (i in 1 until groups.size) {
            result = result.replace("{$i}", groups[i])
        }

        return result
    }
}
