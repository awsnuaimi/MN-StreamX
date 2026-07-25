package com.yourapp.iptv.data.repository

import com.yourapp.iptv.data.mapper.toPlaylist
import com.yourapp.iptv.data.source.m3u.M3uDataSource
import com.yourapp.iptv.data.source.xtream.XtreamDataSource
import com.yourapp.iptv.domain.model.Playlist
import com.yourapp.iptv.domain.repository.PlaylistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PlaylistRepositoryImpl(
    private val m3uDataSource: M3uDataSource,
    private val xtreamDataSource: XtreamDataSource,
    private val channelRepository: ChannelRepositoryImpl
) : PlaylistRepository {

    override suspend fun loadPlaylist(url: String): Playlist = withContext(Dispatchers.IO) {
        // محاولة تحميل كـ M3U أولاً
        val channels = try {
            channelRepository.loadFromM3u(url)
        } catch (e: Exception) {
            // إذا فشل، جرب كـ Xtream (في حال كان الرابط يحتوي على بيانات)
            emptyList()
        }
        channels.toPlaylist("M3U Playlist")
    }
}