package com.yourapp.iptv.domain.repository

import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.AppSettings

interface ChannelRepository {
    /** Optional fast path: selected channel only, without preparing the full queue. */
    suspend fun getPlaybackChannel(id: String): Channel? = null
    suspend fun getPlaybackChannel(source: AppSettings, id: String): Channel? =
        getPlaybackChannels(source, setOf(id)).firstOrNull()
    suspend fun getCachedChannels(source: AppSettings): List<Channel>? = null
    /** Current-source lookup for a small set of IDs, without preparing the full browsing queue. */
    suspend fun getPlaybackChannels(source: AppSettings, ids: Set<String>): List<Channel> =
        getCachedChannels(source).orEmpty().filter { it.id in ids }
    suspend fun getChannels(): List<Channel>
    suspend fun getChannel(id: String): Channel?
    suspend fun loadFromM3u(url: String): List<Channel>
    suspend fun loadFromXtream(serverUrl: String, username: String, password: String): List<Channel>
    suspend fun search(query: String): List<Channel>
    suspend fun getFavorites(): List<Channel>
    suspend fun addFavorite(channel: Channel)
    suspend fun removeFavorite(channel: Channel)
}