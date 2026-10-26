package com.yourapp.iptv.data.repository

import com.yourapp.iptv.domain.model.Playlist
import com.yourapp.iptv.domain.model.AppSettings
import com.yourapp.iptv.domain.repository.ChannelRepository
import com.yourapp.iptv.domain.repository.PlaylistRepository
import com.yourapp.iptv.domain.model.PlaylistCacheKey
import com.yourapp.iptv.domain.model.PlaylistSource
import com.yourapp.iptv.domain.diagnostics.DiagnosticArea
import com.yourapp.iptv.data.diagnostics.DiagnosticFailures
import kotlinx.coroutines.CancellationException

class PlaylistRepositoryImpl(
    private val channelRepository: ChannelRepository
) : PlaylistRepository {
    override suspend fun cachedPlaylist(source: AppSettings): Playlist? =
        channelRepository.getCachedChannels(source)?.takeIf { it.isNotEmpty() }
            ?.let { Playlist(name = source.source.name, channels = it) }

    override suspend fun loadPlaylist(url: String): Playlist = diagnose(DiagnosticArea.PLAYLIST,AppSettings(m3uUrl = url)) {
        Playlist(name = "M3U", channels = channelRepository.loadFromM3u(url))
    }

    override suspend fun loadXtream(serverUrl: String, username: String, password: String): Playlist = diagnose(DiagnosticArea.LIVE_CATALOG,
        AppSettings(source = PlaylistSource.XTREAM,xtreamServer = serverUrl,xtreamUsername = username,xtreamPassword = password)) {
        Playlist(name = "Xtream Codes", channels = channelRepository.loadFromXtream(serverUrl, username, password))
    }
    private suspend fun <T> diagnose(area: DiagnosticArea, source: AppSettings, block: suspend () -> T): T = try { block() }
    catch (error: CancellationException) { throw error }
    catch (error: Exception) { DiagnosticFailures.record(area,error,PlaylistCacheKey.of(source));throw error }
}