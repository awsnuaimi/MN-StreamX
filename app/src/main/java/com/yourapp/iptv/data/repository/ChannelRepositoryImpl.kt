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
import com.yourapp.iptv.domain.model.ChannelFavorites
import com.yourapp.iptv.domain.model.AppSettings
import com.yourapp.iptv.domain.model.PlaylistCacheKey
import com.yourapp.iptv.domain.model.PlaylistSource
import com.yourapp.iptv.domain.repository.ChannelRepository
import com.yourapp.iptv.domain.repository.FavoriteRepository
import kotlinx.coroutines.flow.first
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
    private val snapshot: ChannelSnapshotStore = ChannelSnapshotStore.Disabled,
    private val favorites: FavoriteRepository? = null,
    private val profiles: com.yourapp.iptv.domain.repository.ProfileRepository = com.yourapp.iptv.domain.repository.ProfileRepository.Disabled
) : ChannelRepository {
    private val importLock = Mutex()
    private val snapshotLock = Mutex()
    private var memory: List<Channel>? = null
    private var projection: List<Channel>? = null
    private var projectionScope: String? = null
    private var projectionIds: Set<String>? = null

    override suspend fun getCachedChannels(source: AppSettings): List<Channel>? =
        LoadTimings.measure(LoadTimings.Stage.CACHE_READ) {
            withContext(Dispatchers.IO) {
                snapshotLock.withLock {
                    if (!source.hasSelectedSource() || snapshot.read() != PlaylistCacheKey.of(source)) null
                    else readChannelsLocked().takeIf { it.isNotEmpty() }
                }
            }
        }

    override suspend fun getChannels(): List<Channel> = withContext(Dispatchers.IO) {
        snapshotLock.withLock { readChannelsLocked() }
    }

    override suspend fun getPlaybackChannels(source: AppSettings, ids: Set<String>): List<Channel> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        snapshotLock.withLock {
            if (!source.hasSelectedSource() || snapshot.read() != PlaylistCacheKey.of(source)) return@withLock emptyList()
            val cached = memory
            if (cached != null) cached.filter { it.id in ids }
            else ids.mapNotNull { channelDao.getChannel(it)?.toDomain() }
        }
    }

    private suspend fun readChannelsLocked(): List<Channel> {
        val key = personalKey()
        val favoriteIds = favorites?.observeFavoriteIds(key)?.first()
            ?: favoriteDao.getAllFavorites().map { it.channelId }.toSet()
        val base = memory ?: channelDao.getAllChannels().map { it.toDomain().copy(isFavorite = false) }.also { memory = it }
        projection?.takeIf { projectionScope == key && projectionIds == favoriteIds }?.let { return it }
        return ChannelFavorites.apply(base, favoriteIds).also {
            projection = it;projectionScope = key;projectionIds = favoriteIds.toSet()
        }
    }

    override suspend fun getPlaybackChannel(source: AppSettings, id: String): Channel? =
        getPlaybackChannels(source, setOf(id)).firstOrNull()

    override suspend fun getPlaybackChannel(id: String): Channel? = withContext(Dispatchers.IO) {
        snapshotLock.withLock {
            memory?.firstOrNull { it.id == id } ?: channelDao.getChannel(id)?.toDomain()
        }
    }

    override suspend fun getChannel(id: String): Channel? = withContext(Dispatchers.IO) {
        channelDao.getChannel(id)?.toDomain()
    }

    override suspend fun search(query: String): List<Channel> =
        getChannels().filter { it.name.contains(query, ignoreCase = true) }

    override suspend fun getFavorites(): List<Channel> = getChannels().filter { it.isFavorite }

    override suspend fun addFavorite(channel: Channel) = withContext(Dispatchers.IO) {
        if (favorites != null) favorites.setFavorite(channel.id, true, personalKey())
        else favoriteDao.addFavorite(FavoriteEntity(channel.id))
    }

    override suspend fun removeFavorite(channel: Channel) = withContext(Dispatchers.IO) {
        if (favorites != null) favorites.setFavorite(channel.id, false, personalKey())
        else favoriteDao.removeFavorite(channel.id)
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
                    val fingerprint = PlaylistCacheKey.of(source)
                    val previous = memory
                    if (previous != null && snapshot.read() == fingerprint && sameContent(previous, channels)) {
                        return@withLock readChannelsLocked()
                    }
                    // Invalidate BEFORE Room changes. A crash cannot label new rows as the old account.
                    snapshot.write(null)
                    ChannelCache.clearCache()
                    memory = null;projection = null
                    channelDao.replaceAll(channels.map { it.toEntity() })
                    snapshot.write(fingerprint)
                    memory = channels
                    readChannelsLocked()
                }
            }
        }

    private suspend fun personalKey() = snapshot.read()?.let { com.yourapp.iptv.domain.profile.ProfileScope.key(profiles.state.value.catalog.activeId,it) }

    private fun sameContent(a: List<Channel>, b: List<Channel>): Boolean {
        if (a.size != b.size) return false
        return a.indices.all { index ->
            val old = a[index]; val new = b[index]
            old.id == new.id && old.name == new.name && old.logoUrl == new.logoUrl &&
                old.streamUrl == new.streamUrl && old.group == new.group
        }
    }

    private fun Channel.toEntity() = ChannelEntity(id, name, logoUrl, streamUrl, group)
}