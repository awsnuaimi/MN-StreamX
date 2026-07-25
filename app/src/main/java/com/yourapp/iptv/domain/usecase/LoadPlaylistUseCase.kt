package com.yourapp.iptv.domain.usecase

import com.yourapp.iptv.domain.model.Playlist
import com.yourapp.iptv.domain.repository.PlaylistRepository

class LoadPlaylistUseCase(
    private val playlistRepository: PlaylistRepository
) {
    suspend operator fun invoke(url: String): Playlist {
        return playlistRepository.loadPlaylist(url)
    }
}