package com.yourapp.iptv.domain.repository

import com.yourapp.iptv.domain.model.Channel

interface ChannelRepository {
    suspend fun getChannels(): List<Channel>
    suspend fun search(query: String): List<Channel>
    suspend fun getFavorites(): List<Channel>
    suspend fun addFavorite(channel: Channel)
    suspend fun removeFavorite(channel: Channel)
}