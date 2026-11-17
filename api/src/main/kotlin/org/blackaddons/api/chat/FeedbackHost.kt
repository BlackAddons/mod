package org.blackaddons.api.chat

/** Sounds, titles and text-to-speech. Sound and title calls no-op outside a world. */
interface FeedbackHost {
    /** Plays a sound by id, e.g. `"minecraft:block.note_block.pling"`; unknown ids no-op. Useful [pitch]: 0.5..2.0. */
    fun playSound(soundId: String, volume: Float = 1f, pitch: Float = 1f)

    fun soundIds(): List<String> = emptyList()

    /** Vanilla centered title. `§` codes work; timings are in ticks. */
    fun showTitle(
        title: String,
        subtitle: String? = null,
        fadeIn: Int = 5,
        stay: Int = 40,
        fadeOut: Int = 10,
    )

    /** Speaks [text] via Minecraft's native TTS. [interrupt] stops current speech first. */
    fun narrate(text: String, interrupt: Boolean = true) {}
}
