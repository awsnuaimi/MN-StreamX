package com.yourapp.iptv.data.repository

import com.yourapp.iptv.data.cache.ChannelCache
import com.yourapp.iptv.data.cache.ChannelSnapshotStore
import com.yourapp.iptv.data.local.dao.ChannelDao
import com.yourapp.iptv.data.local.dao.FavoriteDao
import com.yourapp.iptv.data.local.entity.ChannelEntity
import com.yourapp.iptv.data.local.entity.FavoriteEntity
import com.yourapp.iptv.data.mapper.toDomain
import com.yourapp.iptv.data.source.m3u.M3uDataSource
import com.yourapp.iptv.data.source.xtream.XtreamDataSource
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.AppSettings
import com.yourapp.iptv.domain.model.PlaylistCacheKey
import com.yourapp.iptv.domain.model.PlaylistSource
import com.yourapp.iptv.domain.repository.ChannelRepository
import com.yourapp.iptv.util.LoadTimings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ChannelRepositoryImpl(
    private val m3uDataSource: M3uDataSource,
    private val xtreamDataSource: XtreamDataSource,
    private val channelDao: ChannelDao,
    private val favoriteDao: FavoriteDao,
    private val snapshot: ChannelSnapshotStore = ChannelSnapshotStore.Disabled
) : ChannelRepository {
    private val importLock = Mutex()
    private val snapshotLock = Mutex()

    override suspend fun getCachedChannels(source: AppSettings): List<Channel>? =
        LoadTimings.measure(LoadTimings.Stage.CACHE_READ) {
            withContext(Dispatchers.IO) {
                snapshotLock.withLock {
                    if (!source.hasSelectedSource() || snapshot.read() != PlaylistCacheKey.of(source)) null
                    else getChannels().takeIf { it.isNotEmpty() }
                }
            }
        }

    override suspend fun getChannels(): List<Channel> = withContext(Dispatchers.IO) {
        val favorites = favoriteDao.getAllFavorites().map { it.channelId }.toSet()
        channelDao.getAllChannels().map { it.toDomain().copy(isFavorite = it.id in favorites) }
    }

    override suspend fun getChannel(id: String): Channel? = withContext(Dispatchers.IO) {
        channelDao.getChannel(id)?.toDomain()
    }

    override suspend fun search(query: String): List<Channel> =
        getChannels().filter { it.name.contains(query, ignoreCase = true) }

    override suspend fun getFavorites(): List<Channel> = getChannels().filter { it.isFavorite }

    override suspend fun addFavorite(channel: Channel) = withContext(Dispatchers.IO) {
        favoriteDao.addFavorite(FavoriteEntity(channel.id))
    }

    override suspend fun removeFavorite(channel: Channel) = withContext(Dispatchers.IO) {
        favoriteDao.removeFavorite(channel.id)
    }

    override suspend fun loadFromM3u(url: String): List<Channel> = importLock.withLock {
        val channels = m3uDataSource.fetchChannels(url).map { it.toDomain() }
        replaceSnapshot(channels, AppSettings(m3uUrl = url))
    }

    override suspend fun loadFromXtream(serverUrl: String, username: String, password: String): List<Channel> =
        importLock.withLock {
            val channels = withContext(Dispatchers.IO) {
                xtreamDataSource.fetchChannels(serverUrl, username, password).map {
                    Channel(it.channelId, it.name, it.icon, it.streamUrl, it.categoryName)
                }
            }
            replaceSnapshot(channels, AppSettings(xtreamServer = serverUrl, xtreamUsername = username,
                xtreamPassword = password, source = PlaylistSource.XTREAM))
        }

    private suspend fun replaceSnapshot(channels: List<Channel>, source: AppSettings): List<Channel> =
        LoadTimings.measure(LoadTimings.Stage.STORE_WRITE) {
            withContext(Dispatchers.IO) {
                snapshotLock.withLock {
                    currentCoroutineContext().ensureActive()
                    // Invalidate BEFORE Room changes. A crash cannot label new rows as the old account.
                    snapshot.write(null)
                    ChannelCache.clearCache()
                    channelDao.replaceAll(channels.map { it.toEntity() })
                    snapshot.write(PlaylistCacheKey.of(source))
                    getChannels()
                }
            }
        }

    private fun Channel.toEntity() = ChannelEntity(id, name, logoUrl, streamUrl, group)
}