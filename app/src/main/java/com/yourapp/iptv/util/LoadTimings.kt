package com.yourapp.iptv.util

import java.util.concurrent.ConcurrentHashMap

/** Bounded, process-local diagnostics. No URLs, account details, or channel names. */
internal object LoadTimings {
    enum class Stage { CACHE_READ, REFRESH, STORE_WRITE, XTREAM_ACCOUNT, XTREAM_LISTS, ORGANIZE }
    private val elapsed = ConcurrentHashMap<Stage, Long>()

    fun snapshotMillis(): Map<Stage, Long> = elapsed.toMap()

    suspend fun <T> measure(stage: Stage, block: suspend () -> T): T {
        val start = System.nanoTime()
        return try { block() } finally {
            elapsed[stage] = (System.nanoTime() - start) / 1_000_000
        }
    }
}