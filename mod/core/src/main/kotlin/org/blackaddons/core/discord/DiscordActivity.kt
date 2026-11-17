package org.blackaddons.core.discord

import com.google.gson.annotations.SerializedName
import org.blackaddons.core.discord.DiscordActivity.Companion.of

/** Discord's activity payload. Text fields have to be 2-128 characters long, and [of] makes sure they are. */
internal data class DiscordActivity(
    val details: String?,
    val state: String?,
    val timestamps: Timestamps?,
    val assets: Assets?,
    val party: Party?,
    val buttons: List<Button>?,
) {
    data class Timestamps(val start: Long)

    data class Assets(
        @SerializedName("large_image") val largeImage: String?,
        @SerializedName("large_text") val largeText: String?,
    )

    /** [size] is `[current, max]`, Discord shows it as "(3 of 5)" after [state]. */
    data class Party(val id: String, val size: List<Int>)

    data class Button(val label: String, val url: String)

    companion object {
        private const val MIN_TEXT = 2
        private const val MAX_TEXT = 128
        private const val MAX_LABEL = 32

        fun of(
            details: String?,
            state: String? = null,
            startedAt: Long? = null,
            image: String? = null,
            imageText: String? = null,
            party: Party? = null,
            buttons: List<Button> = emptyList(),
        ) = DiscordActivity(
            details = text(details),
            // Discord draws the party size after the state, so without a state there's nothing to attach it to.
            state = text(state),
            timestamps = startedAt?.let(::Timestamps),
            assets = image?.let { Assets(it, text(imageText)) },
            party = party?.takeIf { text(state) != null },
            buttons = buttons.map { it.copy(label = it.label.take(MAX_LABEL)) }.ifEmpty { null },
        )

        // Discord rejects the whole activity if one field is outside 2..128 characters.
        private fun text(value: String?): String? =
            value?.trim()?.takeIf(String::isNotEmpty)?.take(MAX_TEXT)?.padEnd(MIN_TEXT)
    }
}
