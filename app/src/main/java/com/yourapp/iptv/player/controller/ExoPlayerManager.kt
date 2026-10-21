package com.yourapp.iptv.player.controller

import android.content.Context
import com.yourapp.iptv.player.caption.CaptionAudioTap
import com.yourapp.iptv.player.caption.CaptionRenderers
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import java.util.Locale

@androidx.annotation.OptIn(UnstableApi::class)
class ExoPlayerManager(context: Context) {
    private val appContext = context.applicationContext
    private var captionAudio: CaptionAudioTap? = null
    fun configureCaptionAudio(tap: CaptionAudioTap?) { captionAudio = tap }
    var player: ExoPlayer? = null
        private set

    fun initialize() {
        if (player != null) return
        val buffering = DefaultLoadControl.Builder()
            .setBufferDurationsMs(LivePlaybackTuning.MIN_BUFFER_MS, LivePlaybackTuning.MAX_BUFFER_MS,
                LivePlaybackTuning.START_BUFFER_MS, LivePlaybackTuning.REBUFFER_MS)
            .setTargetBufferBytes(LivePlaybackTuning.TARGET_BUFFER_BYTES)
            .setPrioritizeTimeOverSizeThresholds(false)
            .setBackBuffer(0, false)
            .build()
        val builder = captionAudio?.let { ExoPlayer.Builder(appContext, CaptionRenderers(appContext, it)) }
            ?: ExoPlayer.Builder(appContext)
        player = builder.setLoadControl(buffering).build().apply {
            setAudioAttributes(AudioAttributes.DEFAULT, true)
            setHandleAudioBecomingNoisy(true)
        }
    }

    fun play(url: String, mediaId: String = "", title: String? = null) {
        initialize()
        val uri = Uri.parse(url)
        val output = uri.getQueryParameter("output")?.lowercase(Locale.ROOT)
        val item = MediaItem.Builder().setUri(uri).setMediaId(mediaId).apply {
            if (title != null) setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            if (output == "m3u8" || output == "hls") setMimeType(MimeTypes.APPLICATION_M3U8)
        }.build()
        player?.apply {
            setMediaItem(item)
            prepare()
            play()
        }
    }

    fun pause() { player?.pause() }
    fun resume() { player?.play() }
    fun stop() { player?.stop() }
    fun isPlaying(): Boolean = player?.isPlaying == true

    fun release() {
        player?.release()
        player = null
    }
}