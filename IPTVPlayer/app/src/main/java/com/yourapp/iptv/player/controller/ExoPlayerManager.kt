package com.yourapp.iptv.player.controller

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource

class ExoPlayerManager(private val context: Context) {
    private var player: ExoPlayer? = null

    fun initialize() {
        player = ExoPlayer.Builder(context).build()
    }

    fun play(url: String) {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
        val mediaSource = HlsMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(url))
        player?.setMediaSource(mediaSource)
        player?.prepare()
        player?.play()
    }

    fun pause() {
        player?.pause()
    }

    fun resume() {
        player?.play()
    }

    fun stop() {
        player?.stop()
    }

    fun release() {
        player?.release()
        player = null
    }

    fun isPlaying(): Boolean = player?.isPlaying == true
}