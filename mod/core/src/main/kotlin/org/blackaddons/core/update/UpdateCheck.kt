package org.blackaddons.core.update

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.blackaddons.api.Platform
import org.blackaddons.api.chat.ChatClick
import org.blackaddons.api.chat.ChatMessage
import org.blackaddons.api.event.EventListener
import org.blackaddons.api.event.EventPriority
import org.blackaddons.api.event.WorldChangeEvent
import org.blackaddons.api.ui.toast.ToastKind
import org.blackaddons.api.ui.toast.Toasts
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.concurrent.OffThread
import org.blackaddons.api.util.dev.DevFlags
import org.blackaddons.core.backend.Backend
import org.blackaddons.core.backend.gateway.Gateway
import org.blackaddons.core.chat.Brand
import org.blackaddons.core.chat.brand
import org.blackaddons.core.update.UpdateCheck.recheck
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Tells the player when their build is out of date, through the backend (the repo is private, so GitHub Releases can't
 * be read). Never downloads, never blocks startup, never repeats itself. Any failure means silence. Only speaks again
 * when the verdict changes ([recheck]).
 */
object UpdateCheck {

    /** After the server's join spam, while chat is still readable. */
    private const val ANNOUNCE_DELAY_TICKS = 60

    @Volatile private var outcome: Outcome? = null

    /** Last announced outcome. A verdict that got worse (behind -> unsupported) still gets told. */
    @Volatile private var announcedOutcome: Outcome? = null

    private sealed interface Outcome {
        data class Update(val version: String, val url: String) : Outcome

        /** Below the minimum version the backend supports. Calls will start to fail. */
        data class Unsupported(val minimum: String, val url: String) : Outcome
    }

    /**
     * Runs the check off-thread and arms the announcement for the first join. Call once at client init. Skipped in dev,
     * because the dev version (`1.0.0`) would be below any real `minimum`.
     */
    fun start() {
        if (DevFlags.isDevelopment) return
        val current = ModVersion.parse(Platform.blackaddonsVersion) ?: return

        EventListener(WorldChangeEvent::class.java, EventPriority.NORMAL) {
            announceWhenJoined()
        }.register()

        OffThread.run(
            name = "update-check",
            work = { fetch()?.let { evaluate(current, it) } },
            onFailure = { null },
            onResult = { outcome = it },
        )
    }

    /**
     * Asks again after a gateway nudge that the catalog or minimum moved. The answer still comes from `GET
     * /v1/version`. The backend doesn't cut live sockets when the minimum goes up, so a build that's now unsupported
     * disconnects itself.
     */
    fun recheck() {
        if (DevFlags.isDevelopment) return
        val current = ModVersion.parse(Platform.blackaddonsVersion) ?: return
        OffThread.run(
            name = "update-recheck",
            work = { fetch()?.let { evaluate(current, it) } },
            onFailure = { null },
            onResult = { result ->
                if (result == null || result == outcome) return@run
                outcome = result
                if (result is Outcome.Unsupported) Gateway.blockUnsupported()
                announceWhenJoined()
            },
        )
    }

    // Repeats the unsupported notice for a player who asked to reconnect. False if there's no such verdict.
    internal fun remindUnsupported(): Boolean {
        val unsupported = outcome as? Outcome.Unsupported ?: return false
        announceUnsupported(unsupported)
        return true
    }

    /**
     * Prereleases check stable too: the next version of a beta ships there, and "nothing newer on beta" isn't "up to
     * date".
     */
    private fun fetch(): Release? {
        val own = fetch(ReleaseChannel.current)
        if (ReleaseChannel.current == ReleaseChannel.DEFAULT) return own
        return newest(own, fetch(ReleaseChannel.DEFAULT))
    }

    private fun fetch(channel: String): Release? {
        val url = "${Backend.VERSION_URL}?channel=${encode(channel)}&mcVersion=${encode(Platform.minecraftVersion)}"
        val response = Platform.http.getBlocking(url) ?: return null
        if (response.status != 200) return null
        return runCatching { Release.parse(JsonParser.parseString(response.body).asJsonObject) }.getOrNull()
    }

    /** The newer of the two. Takes the other's minimum if it has none, because the minimum is global. */
    private fun newest(own: Release?, stable: Release?): Release? {
        if (own == null || stable == null) return own ?: stable
        val ours = ModVersion.parse(own.latest)
        val theirs = ModVersion.parse(stable.latest)
        val newer = when {
            ours == null -> stable
            theirs == null -> own
            else -> if (theirs > ours) stable else own
        }
        val other = if (newer === own) stable else own
        return newer.copy(minimum = newer.minimum ?: other.minimum)
    }

    private fun evaluate(current: ModVersion, release: Release): Outcome? {
        val minimum = ModVersion.parse(release.minimum)
        if (minimum != null && current < minimum) {
            return Outcome.Unsupported(release.minimum.orEmpty(), release.url)
        }
        val latest = ModVersion.parse(release.latest) ?: return null
        return if (current < latest) Outcome.Update(release.latest, release.url) else null
    }

    /** Posts a few seconds after joining: earlier chat goes nowhere, and the delay clears the server's welcome spam. */
    private fun announceWhenJoined() {
        // Always scheduled, because the same event fires on disconnect, where the check finds no player.
        if (outcome != null && outcome == announcedOutcome) return
        Platform.scheduler.schedule(ANNOUNCE_DELAY_TICKS) {
            val result = outcome ?: return@schedule
            if (result == announcedOutcome || !Platform.player.isInGame) return@schedule
            announcedOutcome = result
            when (result) {
                is Outcome.Update -> announceUpdate(result)
                is Outcome.Unsupported -> announceUnsupported(result)
            }
        }
    }

    private fun announceUpdate(update: Outcome.Update) {
        Toasts.show(
            I18n.tr("blackaddons.update.toast"),
            I18n.tr("blackaddons.update.toast_detail", update.version),
        )
        Platform.chat.local(ChatMessage.build {
            brand()
            append(I18n.tr("blackaddons.update.available", update.version) + " ", Brand.BODY)
            append(
                I18n.tr("blackaddons.update.download"),
                Brand.OK, underlined = true,
                click = ChatClick.OpenUrl(update.url),
                hover = update.url,
            )
        })
    }

    private fun announceUnsupported(unsupported: Outcome.Unsupported) {
        Toasts.show(
            I18n.tr("blackaddons.update.toast_unsupported"),
            I18n.tr("blackaddons.update.toast_detail", unsupported.minimum),
            ToastKind.ERROR,
        )
        Platform.chat.local(ChatMessage.build {
            brand()
            append(I18n.tr("blackaddons.update.unsupported", unsupported.minimum) + " ", Brand.FAIL)
            append(
                I18n.tr("blackaddons.update.download"),
                Brand.OK, underlined = true,
                click = ChatClick.OpenUrl(unsupported.url),
                hover = unsupported.url,
            )
        })
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    /** `url` is a release page, never a jar. */
    private data class Release(val latest: String, val minimum: String?, val url: String) {
        companion object {
            fun parse(o: JsonObject): Release? {
                val latest = string(o, "latest") ?: return null
                val url = string(o, "url") ?: return null
                // Only browser schemes, because the URL goes straight to the OS.
                if (!url.startsWith("https://") && !url.startsWith("http://")) return null
                return Release(latest, string(o, "minimum"), url)
            }

            private fun string(o: JsonObject, key: String): String? =
                o.get(key)?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
        }
    }
}
