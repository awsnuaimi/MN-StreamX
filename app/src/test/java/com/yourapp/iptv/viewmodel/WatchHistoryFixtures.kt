package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.*
import java.io.IOException

internal class HistoryMemory : WatchHistoryRepository {
    val sources = mutableMapOf<String, MutableStateFlow<List<WatchHistoryEntry>>>()
    var failRead = false
    var failWrite = false
    fun records(source: String) = sources.getOrPut(source) { MutableStateFlow(emptyList()) }
    override fun observe(sourceKey: String) = flow {
        if (failRead) throw IOException("History read unavailable")
        emitAll(records(sourceKey))
    }
    override fun recordStarted(sourceKey: String, entry: WatchHistoryEntry) {
        if (failWrite) throw IOException("History write unavailable")
        records(sourceKey).value = WatchHistory.record(records(sourceKey).value, entry)
    }
    override suspend fun remove(sourceKey: String, channelId: String) {
        if (failWrite) throw IOException("History write unavailable")
        records(sourceKey).value = records(sourceKey).value.filterNot { it.channelId == channelId }
    }
    override suspend fun clear(sourceKey: String) {
        if (failWrite) throw IOException("History write unavailable")
        records(sourceKey).value = emptyList()
    }
}

internal class HistorySettings(initial: AppSettings = AppSettings(m3uUrl = "https://unit.test/list")) : SettingsRepository {
    val value = MutableStateFlow(initial)
    override fun observeSettings() = value
    override suspend fun saveSettings(settings: AppSettings) { value.value = settings }
    val key get() = PlaylistCacheKey.of(value.value)
}

internal class HistoryChannels : ChannelRepository {
    var rows = listOf(Channel("a", "A", streamUrl = "https://unit.test/a", group = "One"),
        Channel("b", "B", streamUrl = "https://unit.test/b", group = "One"),
        Channel("c", "C", streamUrl = "https://unit.test/c", group = "Two"))
    var allReads = 0
    var lookupReads = 0
    var requested = emptySet<String>()
    var fail = false
    var customLookup: (suspend (AppSettings, Set<String>) -> List<Channel>)? = null
    override suspend fun getPlaybackChannels(source: AppSettings, ids: Set<String>): List<Channel> {
        lookupReads++; requested = ids
        if (fail) throw IOException("Current channel data unavailable")
        return customLookup?.invoke(source, ids) ?: rows.filter { it.id in ids }
    }
    override suspend fun getCachedChannels(source: AppSettings): List<Channel> { allReads++; return rows }
    override suspend fun getPlaybackChannel(source: AppSettings, id: String) = rows.firstOrNull { it.id == id }
    override suspend fun getPlaybackChannel(id: String) = rows.firstOrNull { it.id == id }
    override suspend fun getChannels(): List<Channel> { allReads++; return rows }
    override suspend fun getChannel(id: String) = error("Unexpected unrelated lookup")
    override suspend fun loadFromM3u(url: String) = error("Unexpected import")
    override suspend fun loadFromXtream(serverUrl: String, username: String, password: String) = error("Unexpected import")
    override suspend fun search(query: String) = error("Unexpected search")
    override suspend fun getFavorites() = emptyList<Channel>()
    override suspend fun addFavorite(channel: Channel) = Unit
    override suspend fun removeFavorite(channel: Channel) = Unit
}