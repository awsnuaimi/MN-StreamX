package com.yourapp.iptv.util

import java.util.concurrent.ConcurrentHashMap

/** Bounded, process-local diagnostics. No URLs, account details, or channel names. */
internal object LoadTimings {
    enum class Stage { CACHE_READ, REFRESH, STORE_WRITE, XTREAM_ACCOUNT, XTREAM_LISTS, ORGANIZE,
        CHANNEL_LOOKUP, FIRST_FRAME }
    private val elapsed = ConcurrentHashMap<Stage, Long>()

    fun snapshotMillis(): Map<Stage, Long> = elapsed.toMap()

    fun recordSince(stage: Stage, startNanos: Long) {
        elapsed[stage] = (System.nanoTime() - startNanos).coerceAtLeast(0) / 1_000_000
    }

    suspend fun <T> measure(stage: Stage, block: suspend () -> T): T {
        val start = System.nanoTime()
        return try { block() } finally {
            recordSince(stage, start)
        }
    }
}