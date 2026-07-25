package com.yourapp.iptv.player.controller

import android.content.Context

class PlayerController(private val context: Context) {
    private val exoPlayerManager = ExoPlayerManager(context)

    fun initialize() {
        exoPlayerManager.initialize()
    }

    fun playUrl(url: String) {
        exoPlayerManager.play(url)
    }

    fun pause() {
        exoPlayerManager.pause()
    }

    fun resume() {
        exoPlayerManager.resume()
    }

    fun stop() {
        exoPlayerManager.stop()
    }

    fun release() {
        exoPlayerManager.release()
    }

    fun isPlaying(): Boolean = exoPlayerManager.isPlaying()
}