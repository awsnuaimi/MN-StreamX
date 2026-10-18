package com.yourapp.iptv.data.repository

import com.yourapp.iptv.data.cache.ChannelCache
import com.yourapp.iptv.data.local.dao.ChannelDao
import com.yourapp.iptv.data.local.dao.FavoriteDao
import com.yourapp.iptv.data.local.entity.ChannelEntity
import com.yourapp.iptv.data.local.entity.FavoriteEntity
import com.yourapp.iptv.data.mapper.toDomain
import com.yourapp.iptv.data.source.m3u.M3uDataSource
import com.yourapp.iptv.data.source.xtream.XtreamDataSource
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.repository.ChannelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ChannelRepositoryImpl(
    private val m3uDataSource: M3uDataSource,
    private val xtreamDataSource: XtreamDataSource,
    private val channelDao: ChannelDao,
    private val favoriteDao: FavoriteDao
) : ChannelRepository {
    private val importLock = Mutex()

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
        withContext(Dispatchers.IO) {
            channelDao.replaceAll(channels.map { it.toEntity() })
            ChannelCache.clearCache()
        }
        getChannels()
    }

    override suspend fun loadFromXtream(serverUrl: String, username: String, password: String): List<Channel> =
        importLock.withLock {
            val channels = withContext(Dispatchers.IO) {
                xtreamDataSource.fetchChannels(serverUrl, username, password).map {
                    Channel(it.channelId, it.name, it.icon, it.streamUrl, it.categoryName)
                }
            }
            if (channels.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    channelDao.replaceAll(channels.map { it.toEntity() })
                    ChannelCache.clearCache()
                }
            }
            getChannels()
        }

    private fun Channel.toEntity() = ChannelEntity(id, name, logoUrl, streamUrl, group)
}