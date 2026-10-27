package com.yourapp.iptv.data.parental

import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.parental.*
import com.yourapp.iptv.domain.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** All read paths, including fast playback/history lookups. Raw persistence stays in the delegate. */
internal class ProtectedChannels(private val raw: ChannelRepository, private val settings: SettingsRepository,
    private val parental: ParentalControl) : ChannelRepository {
    private suspend fun current() = settings.observeSettings().first()
    private suspend fun filtered(source: AppSettings, rows: List<Channel>): List<Channel> = withContext(Dispatchers.Default) {
        val key = PlaylistCacheKey.of(source); val access = parental.state.value
        rows.filter { access.allows(key,it) }
    }
    override suspend fun getCachedChannels(source: AppSettings) = raw.getCachedChannels(source)?.let { filtered(source,it) }
    override suspend fun getPlaybackChannels(source: AppSettings, ids: Set<String>) = filtered(source,raw.getPlaybackChannels(source,ids))
    override suspend fun getPlaybackChannel(source: AppSettings, id: String) = getPlaybackChannels(source,setOf(id)).firstOrNull()
    override suspend fun getPlaybackChannel(id: String) = getPlaybackChannel(current(),id)
    override suspend fun getChannels(): List<Channel> { val source = current(); return getCachedChannels(source).orEmpty() }
    override suspend fun getChannel(id: String) = getPlaybackChannel(id)
    override suspend fun search(query: String) = getChannels().filter { it.name.contains(query,ignoreCase = true) }
    override suspend fun getFavorites() = getChannels().filter { it.isFavorite }
    override suspend fun addFavorite(channel: Channel) {
        if (getPlaybackChannel(channel.id) == null) throw ParentalDenied()
        raw.addFavorite(channel)
    }
    override suspend fun removeFavorite(channel: Channel) = raw.removeFavorite(channel)
    override suspend fun loadFromM3u(url: String): List<Channel> {
        if (!parental.state.value.allows(ParentalSection.LIVE)) return emptyList()
        return filtered(AppSettings(m3uUrl = url),raw.loadFromM3u(url))
    }
    override suspend fun loadFromXtream(serverUrl: String, username: String, password: String): List<Channel> {
        if (!parental.state.value.allows(ParentalSection.LIVE)) return emptyList()
        return filtered(AppSettings(source = PlaylistSource.XTREAM,xtreamServer = serverUrl,
            xtreamUsername = username,xtreamPassword = password),raw.loadFromXtream(serverUrl,username,password))
    }
}

/** Only the PIN-protected editor receives unrestricted group names; it never imports a new catalog. */
internal class RawParentalCatalog(private val raw: ChannelRepository, private val parental: ParentalControl) : ParentalCatalog {
    override suspend fun groups(source: AppSettings): List<String> = withContext(Dispatchers.IO) {
        val access = parental.state.value
        if (!access.ready || access.storageError || (access.configured && !access.unlocked)) throw ParentalDenied()
        val groups = raw.getCachedChannels(source).orEmpty().map { it.group.orEmpty() }.distinct().sorted()
        if (parental.state.value.epoch != access.epoch) throw ParentalDenied()
        groups
    }
}