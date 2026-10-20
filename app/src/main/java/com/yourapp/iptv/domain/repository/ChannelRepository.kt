package com.yourapp.iptv.domain.repository

import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.AppSettings

interface ChannelRepository {
    /** Optional fast path: selected channel only, without preparing the full queue. */
    suspend fun getPlaybackChannel(id: String): Channel? = null
    suspend fun getCachedChannels(source: AppSettings): List<Channel>? = null
    suspend fun getChannels(): List<Channel>
    suspend fun getChannel(id: String): Channel?
    suspend fun loadFromM3u(url: String): List<Channel>
    suspend fun loadFromXtream(serverUrl: String, username: String, password: String): List<Channel>
    suspend fun search(query: String): List<Channel>
    suspend fun getFavorites(): List<Channel>
    suspend fun addFavorite(channel: Channel)
    suspend fun removeFavorite(channel: Channel)
}