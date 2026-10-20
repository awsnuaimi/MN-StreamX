package com.yourapp.iptv.domain.repository

import com.yourapp.iptv.domain.model.Playlist
import com.yourapp.iptv.domain.model.AppSettings

interface PlaylistRepository {
    suspend fun cachedPlaylist(source: AppSettings): Playlist? = null
    suspend fun loadPlaylist(url: String): Playlist
    suspend fun loadXtream(serverUrl: String, username: String, password: String): Playlist
}