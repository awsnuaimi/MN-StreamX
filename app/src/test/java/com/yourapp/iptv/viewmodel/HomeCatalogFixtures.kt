package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.organization.*
import com.yourapp.iptv.domain.repository.*
import kotlinx.coroutines.flow.*

internal val CATALOG_A = AppSettings(m3uUrl = "https://catalog.invalid/a")
internal val CATALOG_B = AppSettings(m3uUrl = "https://catalog.invalid/b")
internal fun catalogRow(id: String) = Channel(id,id,streamUrl = "https://catalog.invalid/$id.ts",group = "News")
internal fun catalogSnapshot(source: AppSettings, id: String = "saved", refreshedAt: Long = 0): HomeStartupSnapshot {
    val config = OrganizationConfig(sourceKey = OrganizationNames.sourceKey(source))
    return HomeStartupSnapshot(config,ChannelOrganizer.project(listOf(catalogRow(id)),config),refreshedAt)
}

internal class HomeCatalogSettings(initial: AppSettings = CATALOG_A) : SettingsRepository {
    val changes = MutableStateFlow(Result.success(initial))
    override fun observeSettings() = changes.map { it.getOrThrow() }
    override suspend fun saveSettings(settings: AppSettings) { changes.value = Result.success(settings) }
}

internal class HomeCatalogLists : PlaylistRepository {
    val calls = mutableListOf<String>()
    val cacheReads = mutableListOf<AppSettings>()
    var cached: suspend (AppSettings) -> Playlist? = { null }
    var network: suspend (String) -> Playlist = { Playlist("network",listOf(catalogRow("network"))) }
    override suspend fun cachedPlaylist(source: AppSettings): Playlist? { cacheReads.add(source);return cached(source) }
    override suspend fun loadPlaylist(url: String): Playlist { calls.add(url);return network(url) }
    override suspend fun loadXtream(serverUrl: String, username: String, password: String) = loadPlaylist(serverUrl)
}

internal data class HomeCatalogWrite(val source: AppSettings, val config: OrganizationConfig,
    val catalog: OrganizedChannels, val refreshedAt: Long)
internal class HomeCatalogSnapshots : HomeStartupRepository {
    var read: suspend (AppSettings) -> HomeStartupSnapshot? = { null }
    var beforeWrite: suspend () -> Unit = {}
    val writes = mutableListOf<HomeCatalogWrite>()
    override suspend fun restore(source: AppSettings) = read(source)
    override suspend fun save(source: AppSettings, config: OrganizationConfig, catalog: OrganizedChannels, refreshedAt: Long) {
        beforeWrite();writes.add(HomeCatalogWrite(source,config,catalog,refreshedAt))
    }
}

internal class HomeCatalogTarget : HomeCatalogController.Target {
    var shown = emptyList<Channel>()
    val restored = mutableListOf<HomeStartupSnapshot>()
    val failed = mutableListOf<Exception>()
    var loads = 0
    var acceptRestore = true
    var onRestore: (HomeStartupSnapshot) -> Unit = {}
    override val hasChannels get() = shown.isNotEmpty()
    override fun loading() { loads++ }
    override fun restore(snapshot: HomeStartupSnapshot): Boolean {
        restored.add(snapshot);onRestore(snapshot)
        if (acceptRestore) shown = snapshot.catalog.channels
        return acceptRestore
    }
    override fun channels(value: List<Channel>, refreshing: Boolean) { shown = value }
    override fun failed(error: Exception) { failed.add(error) }
    fun clear() { shown = emptyList() }
}