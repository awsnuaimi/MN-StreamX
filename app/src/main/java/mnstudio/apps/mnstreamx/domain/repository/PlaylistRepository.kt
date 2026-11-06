package mnstudio.apps.mnstreamx.domain.repository

import mnstudio.apps.mnstreamx.domain.model.Playlist
import mnstudio.apps.mnstreamx.domain.model.AppSettings

interface PlaylistRepository {
    suspend fun cachedPlaylist(source: AppSettings): Playlist? = null
    suspend fun loadPlaylist(url: String): Playlist
    suspend fun loadXtream(serverUrl: String, username: String, password: String): Playlist
}