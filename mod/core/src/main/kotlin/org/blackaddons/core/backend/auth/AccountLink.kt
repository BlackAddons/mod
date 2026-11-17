package org.blackaddons.core.backend.auth

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.blackaddons.api.Platform
import org.blackaddons.api.chat.ChatClick
import org.blackaddons.api.chat.ChatMessage
import org.blackaddons.api.util.I18n
import org.blackaddons.api.util.concurrent.OffThread
import org.blackaddons.core.backend.Backend
import org.blackaddons.core.backend.Http
import org.blackaddons.core.chat.Brand
import org.blackaddons.core.chat.brand
import java.awt.Color
import kotlin.math.max

/**
 * Account linking without a password. The backend issues a short code the player types in a browser. This is also the
 * only way in without an email. The same code signs in or links to an existing account, and `GET /v1/me` decides which
 * links to show. Discord linking is in the panel. Answers go to chat, because the player has to copy or click them.
 */
internal object AccountLink {

    fun requestCode() {
        OffThread.run(
            name = "account-link",
            work = { code()?.copy(account = accountName()) },
            onFailure = { null },
            onResult = { answer -> if (answer == null) failed() else postCode(answer) },
        )
    }

    /**
     * Null when unlinked or unknown. If we don't know, one extra link is cheap. Guessing "linked" would hide the link
     * they need.
     */
    private fun accountName(): String? {
        val response = Http.get(Backend.ME_URL, authenticated = true)?.takeIf { it.status == 200 } ?: return null
        val json = runCatching { JsonParser.parseString(response.body).asJsonObject }.getOrNull() ?: return null
        return json.get("nickname")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
    }

    private fun code(): Answer? {
        val response = Http.send(Backend.LINK_CODE_URL, method = Http.Method.POST, authenticated = true)
        val json = response?.takeIf { it.status == 200 }
            ?.let { runCatching { JsonParser.parseString(it.body).asJsonObject }.getOrNull() } ?: return null
        val code = json.get("code")?.takeIf { !it.isJsonNull }?.asString
        return if (code.isNullOrBlank()) null else Answer(code, minutes(json))
    }

    private fun postCode(answer: Answer) {
        Platform.chat.local(ChatMessage.build {
            brand()
            answer.account?.let { append(I18n.tr("blackaddons.link.linked_as", it) + " ", Brand.BODY) }
            append(I18n.tr("blackaddons.link.code") + " ", Brand.BODY)
            append(
                answer.code,
                Brand.OK, bold = true, underlined = true,
                click = ChatClick.CopyToClipboard(answer.code),
                hover = I18n.tr("blackaddons.link.copy_hover"),
            )
            append(" " + I18n.tr("blackaddons.link.expires", answer.minutes) + " ", Brand.BODY)
            // Always shown: the code also signs in account holders without a password.
            append(
                I18n.tr("blackaddons.link.sign_in"),
                Brand.OK, underlined = true,
                click = ChatClick.OpenUrl(Backend.SIGN_IN_URL),
                hover = Backend.SIGN_IN_URL,
            )
            if (answer.account == null) {
                append(" ", Brand.BODY)
                append(
                    I18n.tr("blackaddons.link.attach"),
                    Brand.OK, underlined = true,
                    click = ChatClick.OpenUrl(Backend.LINK_MINECRAFT_URL),
                    hover = Backend.LINK_MINECRAFT_URL,
                )
            }
        })
    }

    /** Never zero, because "0 min" looks expired. */
    private fun minutes(json: JsonObject): Int {
        val seconds = json.get("expiresInSeconds")?.takeIf { !it.isJsonNull }?.asInt ?: 0
        return max(1, seconds / 60)
    }

    private fun failed() = say("blackaddons.link.failed", Brand.FAIL)

    private fun say(key: String, color: Color) {
        Platform.chat.local(ChatMessage.build {
            brand()
            append(I18n.tr(key), color)
        })
    }

    private data class Answer(val code: String, val minutes: Int, val account: String? = null)
}
