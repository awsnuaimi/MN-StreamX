package com.yourapp.iptv.data.cache

import com.yourapp.iptv.domain.model.Program

object EpgCache {
    private val cachedEpg = mutableMapOf<String, List<Program>>()
    private var lastUpdateTime: Long = 0L
    private const val CACHE_DURATION_MS = 10 * 60 * 1000L // 10 دقائق

    fun getPrograms(channelId: String): List<Program>? {
        return if (isCacheValid()) cachedEpg[channelId] else null
    }

    fun savePrograms(channelId: String, programs: List<Program>) {
        cachedEpg[channelId] = programs
        lastUpdateTime = System.currentTimeMillis()
    }

    fun isCacheValid(): Boolean {
        return (System.currentTimeMillis() - lastUpdateTime) < CACHE_DURATION_MS
    }

    fun clearCache() {
        cachedEpg.clear()
        lastUpdateTime = 0L
    }
}