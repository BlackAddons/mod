package org.blackaddons.api.util.concurrent
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/** Named (`blackaddons-<name>`) daemon threads. Use [OffThread] for a job whose result a screen waits on. */
object Workers {

    /** Started. */
    fun start(name: String, body: () -> Unit): Thread = daemon(body, name).apply { start() }

    fun single(name: String): ExecutorService = Executors.newSingleThreadExecutor(factory(name))

    fun scheduled(name: String): ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(factory(name))

    /** Numbered `blackaddons-<name>-1` and up. */
    fun pool(name: String, size: Int): ExecutorService {
        val number = AtomicInteger()
        return Executors.newFixedThreadPool(size) { daemon(it::run, "$name-${number.incrementAndGet()}") }
    }

    private fun factory(name: String) = ThreadFactory { daemon(it::run, name) }

    private fun daemon(body: () -> Unit, name: String) = Thread(body, "$PREFIX$name").apply { isDaemon = true }

    private const val PREFIX = "blackaddons-"
}
