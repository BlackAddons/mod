package org.blackaddons.core.chat

import org.blackaddons.api.chat.ChatMessage
import org.blackaddons.api.ui.theme.Theme
import org.blackaddons.api.util.I18n
import java.awt.Color

internal object Brand {
    val FROM = Theme.BRAND_FROM
    val TO = Theme.BRAND_TO

    /** For parts without a status of their own. */
    val BODY = Color(0xAAAAAA)
    val OK = Color(124, 224, 162)
    val FAIL = Color(255, 120, 120)
}

internal fun ChatMessage.Builder.brand(): ChatMessage.Builder =
    appendGradient(I18n.tr("blackaddons.chat.prefix"), Brand.FROM, Brand.TO, bold = true)
