package com.yourapp.iptv.data.repository

import com.yourapp.iptv.domain.model.Playlist
import com.yourapp.iptv.domain.model.AppSettings
import com.yourapp.iptv.domain.repository.ChannelRepository
import com.yourapp.iptv.domain.repository.PlaylistRepository

class PlaylistRepositoryImpl(
    private val channelRepository: ChannelRepository
) : PlaylistRepository {
    override suspend fun cachedPlaylist(source: AppSettings): Playlist? =
        channelRepository.getCachedChannels(source)?.takeIf { it.isNotEmpty() }
            ?.let { Playlist(name = source.source.name, channels = it) }

    override suspend fun loadPlaylist(url: String): Playlist =
        Playlist(name = "M3U", channels = channelRepository.loadFromM3u(url))

    override suspend fun loadXtream(serverUrl: String, username: String, password: String): Playlist =
        Playlist(name = "Xtream Codes", channels = channelRepository.loadFromXtream(serverUrl, username, password))
}