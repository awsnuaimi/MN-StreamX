package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.organization.*
import com.yourapp.iptv.domain.repository.*
import kotlinx.coroutines.flow.*
import java.io.IOException

internal class OrganizationMemory(initial: OrganizationConfig = OrganizationConfig(sourceKey = "test-source", enabled = true)) : OrganizationRepository {
    val value = MutableStateFlow(initial)
    var writes = 0
    var failRead = false
    var failSave = false
    override fun observe() = flow {
        if (failRead) throw IOException("Synthetic organization read")
        value.collect { emit(it) }
    }
    override suspend fun save(value: OrganizationConfig) {
        writes++
        if (failSave) throw IOException("Synthetic organization save")
        this.value.value = value.copy(revision = value.revision + 1)
    }
}

internal class OrganizationChannelsFixture : ChannelRepository {
    var rows = listOf(
        channel("s1", "AR SYRIA HD", "Syria 1"), channel("s2", "AR SYRIA FHD", "Syria 2"),
        channel("e1", "AR EGYPT", "Egypt 1"), channel("b2", "AR SPORTS", "beIN Sports 2"),
        channel("d1", "DE SPORT", "German 1"), channel("p1", "PL NEWS", "Polish 1"))
    var fail = false
    var reads = 0
    var imports = 0
    override suspend fun getChannels(): List<Channel> { reads++; if (fail) throw IOException("Synthetic channel read"); return rows }
    override suspend fun getChannel(id: String) = rows.firstOrNull { it.id == id }
    override suspend fun loadFromM3u(url: String): List<Channel> { imports++; return rows }
    override suspend fun loadFromXtream(serverUrl: String, username: String, password: String): List<Channel> { imports++; return rows }
    override suspend fun search(query: String) = rows.filter { it.name.contains(query) }
    override suspend fun getFavorites() = rows.filter { it.isFavorite }
    override suspend fun addFavorite(channel: Channel) = Unit
    override suspend fun removeFavorite(channel: Channel) = Unit
    companion object {
        fun channel(id: String, group: String, name: String = id) = Channel(id, name, streamUrl = "https://unit.test/$id", group = group)
    }
}

internal class OrganizationSettingsFixture : SettingsRepository {
    val value = MutableStateFlow(AppSettings(m3uUrl = "https://unit.test/list.m3u"))
    override fun observeSettings() = value
    override suspend fun saveSettings(settings: AppSettings) { value.value = settings }
}

internal class OrganizationPlaylistsFixture(private val channels: OrganizationChannelsFixture) : PlaylistRepository {
    var reads = 0
    override suspend fun loadPlaylist(url: String): Playlist { reads++; return Playlist("Test", channels.rows) }
    override suspend fun loadXtream(serverUrl: String, username: String, password: String) = loadPlaylist(serverUrl)
}