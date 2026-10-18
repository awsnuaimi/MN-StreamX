package com.yourapp.iptv.player.controller

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

@androidx.annotation.OptIn(UnstableApi::class)
class PlayerController(context: Context) {
    private val manager = ExoPlayerManager(context.applicationContext)
    val player: ExoPlayer? get() = manager.player

    fun initialize() = manager.initialize()
    fun playUrl(url: String) = manager.play(url)
    fun pause() = manager.pause()
    fun resume() = manager.resume()
    fun stop() = manager.stop()
    fun release() = manager.release()
    fun isPlaying(): Boolean = manager.isPlaying()
}