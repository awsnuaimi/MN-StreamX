package com.yourapp.iptv.domain.repository

import kotlinx.coroutines.flow.Flow

interface FavoriteRepository {
    fun observeFavoriteIds(): Flow<Set<String>>
    suspend fun toggleFavorite(channelId: String)
}