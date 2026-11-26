package org.blackaddons.core.profile

import org.blackaddons.api.util.dev.DevFileLogger
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Phase timings of one profile lookup, written to the `pv-load` dev log (DevMode -> Verbose Logging). Bound to the
 * lookup's worker thread, so the parser can time a phase without passing a trace down to every private function.
 */
internal class LoadTrace private constructor(private val who: String) {

    private class Span(var time: Duration = Duration.ZERO, var count: Int = 0)

    private val started = TimeSource.Monotonic.markNow()
    private val spans = LinkedHashMap<String, Span>()
    private val notes = mutableListOf<String>()

    private fun <T> time(name: String, block: () -> T): T {
        val start = TimeSource.Monotonic.markNow()
        try {
            return block()
        } finally {
            spans.getOrPut(name, ::Span).apply {
                time += start.elapsedNow()
                count++
            }
        }
    }

    private fun line(outcome: String): String {
        val phases = spans.entries.joinToString { (name, span) ->
            val ms = span.time.inWholeMilliseconds
            if (span.count > 1) "$name $ms ms x${span.count}" else "$name $ms ms"
        }
        val extra = if (notes.isEmpty()) "" else "; " + notes.joinToString()
        return "$who: $outcome in ${started.elapsedNow().inWholeMilliseconds} ms ($phases$extra)"
    }

    companion object {
        private val log = DevFileLogger.named("pv-load")
        private val current = ThreadLocal<LoadTrace?>()

        /** Times [block] as part of the lookup running on this thread. Outside a lookup it just runs [block]. */
        fun <T> span(name: String, block: () -> T): T {
            val trace = current.get() ?: return block()
            return trace.time(name, block)
        }

        fun note(text: String) {
            current.get()?.notes?.add(text)
        }

        fun <T> lookup(who: String, outcome: (T) -> String, block: () -> T): T {
            val trace = LoadTrace(who)
            current.set(trace)
            try {
                return block().also { log.write(trace.line(outcome(it))) }
            } finally {
                current.remove()
            }
        }

        fun write(line: String) = log.write(line)
    }
}
