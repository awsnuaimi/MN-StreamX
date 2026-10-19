package com.yourapp.iptv.data.repository

import com.yourapp.iptv.data.local.dao.FavoriteDao
import com.yourapp.iptv.domain.repository.FavoriteRepository
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class FavoriteRepositoryImpl(private val dao: FavoriteDao) : FavoriteRepository {
    override fun observeFavoriteIds() = dao.observeFavoriteIds()
        .map { it.toSet() }.distinctUntilChanged()

    override suspend fun toggleFavorite(channelId: String) {
        require(channelId.isNotBlank())
        dao.toggleFavorite(channelId)
    }
}