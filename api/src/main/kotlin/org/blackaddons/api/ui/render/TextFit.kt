package org.blackaddons.api.ui.render

import org.blackaddons.api.util.graphemes
import org.blackaddons.api.util.takeValidUtf16

internal object TextFit {

    fun ellipsize(text: String, maxWidth: Number, size: Number, font: Font): String {
        val max = maxWidth.toFloat()
        if (Render.textWidth(text, size, font) <= max) return text
        /*
         * The search probes are new layouts, so a cut drawn every frame is remembered. Re-measuring the result
         * (a cached layout) catches a font that changed under it.
         */
        val key = Cut(text, max, size.toFloat(), font)
        cuts[key]?.takeIf { Render.textWidth(it, size, font) <= max }?.let { return it }
        val cut = ellipsize(text, max) { Render.textWidth(it, size, font) }
        return cut.takeIf { Render.textWidth(it, size, font) <= max }.orEmpty().also { cuts[key] = it }
    }

    private data class Cut(val text: String, val maxWidth: Float, val size: Float, val font: Font)

    private val cuts = object : LinkedHashMap<Cut, String>(CUT_CACHE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Cut, String>) = size > CUT_CACHE
    }

    private const val CUT_CACHE = 512

    fun ellipsize(text: String, maxWidth: Number, measure: (String) -> Float): String {
        val max = maxWidth.toFloat()
        if (measure(text) <= max) return text
        var low = 0
        var high = text.length - 1
        while (low < high) {
            val mid = (low + high + 1) / 2
            val candidate = text.takeValidUtf16(mid) + ELLIPSIS
            if (measure(candidate) <= max) low = mid else high = mid - 1
        }
        val resultText = text.takeValidUtf16(low)
        return if (resultText.isEmpty()) ELLIPSIS else resultText + ELLIPSIS
    }

    fun wrapText(text: String, maxWidth: Number, size: Number, font: Font): List<String> {
        if ('\n' in text) return text.split('\n').flatMap { wrapText(it, maxWidth, size, font) }
        val limit = maxWidth.toFloat()
        if (text.isEmpty() || limit <= 0f) return listOf(text)

        val lines = ArrayList<String>()
        var line = ""

        for (rawWord in text.split(' ')) {
            if (rawWord.isEmpty()) continue
            var word = rawWord
            while (word.length > 1 && Render.textWidth(word, size, font) > limit) {
                if (line.isNotEmpty()) { lines += line; line = "" }
                val clusters = word.graphemes()
                var subLen = 0
                for (g in clusters) {
                    val nextLen = subLen + g.length
                    if (Render.textWidth(word.substring(0, nextLen), size, font) <= limit) {
                        subLen = nextLen
                    } else {
                        break
                    }
                }
                if (subLen == 0 && clusters.isNotEmpty()) {
                    subLen = clusters[0].length
                }
                lines += word.substring(0, subLen)
                word = word.substring(subLen)
            }
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (line.isEmpty() || Render.textWidth(candidate, size, font) <= limit) {
                line = candidate
            } else {
                lines += line
                line = word
            }
        }
        if (line.isNotEmpty()) lines += line
        return lines.ifEmpty { listOf("") }
    }

    private const val ELLIPSIS = "..."
}
