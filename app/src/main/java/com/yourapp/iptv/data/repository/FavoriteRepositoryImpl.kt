package com.yourapp.iptv.data.repository

import com.yourapp.iptv.data.local.dao.ScopedFavoriteDao
import com.yourapp.iptv.data.local.entity.ScopedFavoriteEntity
import com.yourapp.iptv.domain.model.PlaylistCacheKey
import com.yourapp.iptv.domain.repository.FavoriteRepository
import com.yourapp.iptv.domain.repository.SubscriptionRepository
import kotlinx.coroutines.flow.*
import java.io.IOException

class FavoriteRepositoryImpl(private val dao: ScopedFavoriteDao,
    private val subscriptions: SubscriptionRepository) : FavoriteRepository {
    override fun observeFavoriteIds(sourceKey: String?): Flow<Set<String>> = flow {
        if (sourceKey == null) { emit(emptySet()); return@flow }
        require(valid(sourceKey))
        migrate()
        emitAll(dao.observeIds(sourceKey).map { it.toSet() }.distinctUntilChanged())
    }
    override suspend fun toggleFavorite(channelId: String, sourceKey: String?) {
        require(channelId.isNotBlank() && channelId.length <= 512)
        val key = requireSource(sourceKey)
        dao.toggle(key, channelId)
    }
    override suspend fun setFavorite(channelId: String, selected: Boolean, sourceKey: String?) {
        require(channelId.isNotBlank() && channelId.length <= 512)
        val key = requireSource(sourceKey)
        if (selected) dao.add(ScopedFavoriteEntity(key, channelId)) else dao.remove(key, channelId)
    }
    private suspend fun requireSource(sourceKey: String?): String {
        if (sourceKey == null || !valid(sourceKey)) throw IOException("Favorite source unavailable")
        val catalog = subscriptions.observeSubscriptions().first()
        if (catalog.entries.none { it.settings.hasSelectedSource() && PlaylistCacheKey.of(it.settings) == sourceKey })
            throw IOException("Favorite source changed")
        catalog.legacySourceKey?.let { dao.migrateLegacy(it) }
        return sourceKey
    }
    private suspend fun migrate() {
        subscriptions.observeSubscriptions().first().legacySourceKey?.let { dao.migrateLegacy(it) }
    }
    private fun valid(key: String) = key.matches(Regex("[0-9a-f]{64}"))
}