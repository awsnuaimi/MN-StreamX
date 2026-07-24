package com.yourapp.iptv.data.cache

import com.yourapp.iptv.domain.model.Channel

object ChannelCache {
    private var cachedChannels: List<Channel>? = null
    private var lastUpdateTime: Long = 0L
    private const val CACHE_DURATION_MS = 5 * 60 * 1000L // 5 دقائق

    fun getChannels(): List<Channel>? {
        return if (isCacheValid()) cachedChannels else null
    }

    fun saveChannels(channels: List<Channel>) {
        cachedChannels = channels
        lastUpdateTime = System.currentTimeMillis()
    }

    fun isCacheValid(): Boolean {
        return cachedChannels != null && 
               (System.currentTimeMillis() - lastUpdateTime) < CACHE_DURATION_MS
    }

    fun clearCache() {
        cachedChannels = null
        lastUpdateTime = 0L
    }
}