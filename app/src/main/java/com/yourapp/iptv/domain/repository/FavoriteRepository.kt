package com.yourapp.iptv.domain.repository

import kotlinx.coroutines.flow.Flow

interface FavoriteRepository {
    fun observeFavoriteIds(sourceKey: String? = null): Flow<Set<String>>
    suspend fun toggleFavorite(channelId: String, sourceKey: String? = null)
    suspend fun setFavorite(channelId: String, selected: Boolean, sourceKey: String? = null)
}