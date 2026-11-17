package org.blackaddons.platform.chat

import org.blackaddons.api.chat.ChatLine

interface StoredChatLine : ChatLine {

    fun blackaddonsSetRaw(raw: String?)

    var blackaddonsAddedAt: Long

    fun blackaddonsCopyStateTo(other: StoredChatLine)
}
