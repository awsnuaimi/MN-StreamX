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
import kotlinx.coroutines.withContext

class ChannelRepositoryImpl(
    private val m3uDataSource: M3uDataSource,
    private val xtreamDataSource: XtreamDataSource,
    private val channelDao: ChannelDao,
    private val favoriteDao: FavoriteDao
) : ChannelRepository {

    override suspend fun getChannels(): List<Channel> = withContext(Dispatchers.IO) {
        // 1. التحقق من الـ Cache
        ChannelCache.getChannels()?.let { return@withContext it }

        // 2. جلب القنوات من قاعدة البيانات
        val entities = channelDao.getAllChannels()
        if (entities.isNotEmpty()) {
            val channels = entities.map { it.toDomain() }
            ChannelCache.saveChannels(channels)
            return@withContext channels
        }

        // 3. إذا لم يوجد شيء، نرجع قائمة فارغة (سيتم التحميل لاحقاً)
        emptyList()
    }

    override suspend fun search(query: String): List<Channel> = withContext(Dispatchers.IO) {
        // بحث بسيط في القنوات المحملة
        getChannels().filter { it.name.contains(query, ignoreCase = true) }
    }

    override suspend fun getFavorites(): List<Channel> = withContext(Dispatchers.IO) {
        val favoriteIds = favoriteDao.getAllFavorites().map { it.channelId }
        getChannels().filter { it.id in favoriteIds }
    }

    override suspend fun addFavorite(channel: Channel) {
        withContext(Dispatchers.IO) {
            favoriteDao.addFavorite(FavoriteEntity(channel.id))
        }
    }

    override suspend fun removeFavorite(channel: Channel) {
        withContext(Dispatchers.IO) {
            favoriteDao.removeFavorite(channel.id)
        }
    }

    // دوال مساعدة لتحميل القنوات من المصادر المختلفة
    suspend fun loadFromM3u(url: String): List<Channel> = withContext(Dispatchers.IO) {
        val dtos = m3uDataSource.fetchChannels(url)
        val channels = dtos.map { it.toDomain() }
        // حفظ في قاعدة البيانات
        val entities = channels.map { 
            ChannelEntity(it.id, it.name, it.logoUrl, it.streamUrl, it.group)
        }
        channelDao.insertChannels(entities)
        ChannelCache.saveChannels(channels)
        channels
    }

    suspend fun loadFromXtream(serverUrl: String, username: String, password: String): List<Channel> = withContext(Dispatchers.IO) {
        val dtos = xtreamDataSource.fetchChannels(serverUrl, username, password)
        val channels = dtos.map { 
            Channel(
                id = it.streamId,
                name = it.name,
                logoUrl = it.icon,
                streamUrl = it.streamUrl,
                group = it.categoryId
            )
        }
        // حفظ في قاعدة البيانات
        val entities = channels.map { 
            ChannelEntity(it.id, it.name, it.logoUrl, it.streamUrl, it.group)
        }
        channelDao.insertChannels(entities)
        ChannelCache.saveChannels(channels)
        channels
    }
}