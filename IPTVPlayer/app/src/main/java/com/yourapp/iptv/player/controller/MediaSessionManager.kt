package com.yourapp.iptv.player.controller

import android.content.Context
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.ListenableFuture

class MediaSessionManager(private val context: Context) {
    private var mediaSession: MediaSession? = null

    fun initialize(playerController: PlayerController) {
        // سيتم تنفيذ هذا لاحقاً عند ربط الخدمة
        // في هذه المرحلة سنضع الأساس
    }

    fun release() {
        mediaSession?.release()
        mediaSession = null
    }
}