package org.blackaddons.platform.impl.chat
import com.mojang.text2speech.Narrator
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundSource
import org.blackaddons.api.chat.FeedbackHost
import org.blackaddons.platform.compat.showTitleCard
import org.blackaddons.platform.util.onClient

/**
 * [FeedbackHost]: titles, sounds and narration. Calls hop to the client thread (they touch the render pipeline),
 * lookups are plain data.
 */
object MinecraftFeedback : FeedbackHost {

    override fun playSound(soundId: String, volume: Float, pitch: Float) = onClient { mc ->
        val player = mc.player ?: return@onClient
        val id = runCatching { Identifier.parse(soundId) }.getOrNull() ?: return@onClient
        val sound = BuiltInRegistries.SOUND_EVENT.getValue(id) ?: return@onClient
        player.playSound(sound, volume, pitch)
    }

    private val cachedSoundIds: List<String> by lazy {
        BuiltInRegistries.SOUND_EVENT.keySet().map { it.toString() }.sorted()
    }

    override fun soundIds(): List<String> = cachedSoundIds

    override fun showTitle(title: String, subtitle: String?, fadeIn: Int, stay: Int, fadeOut: Int) = onClient { mc ->
        mc.showTitleCard(
            title = Component.literal(title),
            subtitle = Component.literal(subtitle ?: ""),
            fadeIn = fadeIn,
            stay = stay,
            fadeOut = fadeOut,
        )
    }

    override fun narrate(text: String, interrupt: Boolean) = onClient { mc ->
        runCatching {
            val narrator = Narrator.getNarrator()
            if (narrator.active()) {
                val voiceVol = mc.options.getFinalSoundSourceVolume(SoundSource.VOICE)
                val volume = if (voiceVol <= 0f) 1f else voiceVol
                if (interrupt) narrator.clear()
                narrator.say(text, interrupt, volume)
            }
        }
    }
}
