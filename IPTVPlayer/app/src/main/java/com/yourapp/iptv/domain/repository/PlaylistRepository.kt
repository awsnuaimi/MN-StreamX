package com.yourapp.iptv.domain.repository

import com.yourapp.iptv.domain.model.Playlist

interface PlaylistRepository {
    suspend fun loadPlaylist(url: String): Playlist
}