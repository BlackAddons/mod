package org.blackaddons.core.font

import java.util.concurrent.atomic.AtomicInteger

class GlobalFontRetryGate {
    private val generation = AtomicInteger()

    @Volatile private var failedGeneration = -1

    fun requestChanged(): Int = generation.incrementAndGet()

    fun current(): Int = generation.get()

    fun canAttempt(): Boolean = failedGeneration != generation.get()

    fun markFailed(token: Int) {
        if (token == generation.get()) failedGeneration = token
    }

    fun environmentChanged() {
        failedGeneration = -1
    }
}
