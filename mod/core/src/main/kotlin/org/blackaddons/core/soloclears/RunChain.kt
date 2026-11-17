package org.blackaddons.core.soloclears

import org.blackaddons.api.util.Hashes

/**
 * Recording commitments: `sha256(previous || bytes since last)`. The server can't verify them live, but the uploaded
 * file has to reproduce its chain, so a forgery would have to be made live. Never compute committed chunks again.
 * Resends send the same bytes.
 */
internal object RunChain {

    val EMPTY: ByteArray = ByteArray(0)

    fun link(head: ByteArray, chunk: ByteArray): ByteArray = Hashes.sha256(head, chunk)
}
