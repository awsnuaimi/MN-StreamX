package com.yourapp.iptv.data.repository

import com.yourapp.iptv.domain.model.Playlist
import com.yourapp.iptv.domain.repository.ChannelRepository
import com.yourapp.iptv.domain.repository.PlaylistRepository

class PlaylistRepositoryImpl(
    private val channelRepository: ChannelRepository
) : PlaylistRepository {
    override suspend fun loadPlaylist(url: String): Playlist =
        Playlist(name = "M3U", channels = channelRepository.loadFromM3u(url))
}
