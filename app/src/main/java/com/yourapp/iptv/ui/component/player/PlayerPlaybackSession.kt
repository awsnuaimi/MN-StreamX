package com.yourapp.iptv.ui.component.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.player.controller.PlayerController
import com.yourapp.iptv.util.LoadTimings

@androidx.annotation.OptIn(UnstableApi::class)
internal class PlayerPlaybackSession(
    private val controller: PlayerController,
    initialChannel: Channel,
    private val onError: (String, String?) -> Unit,
    private val onBuffering: (String, Boolean) -> Unit,
    private val onStarted: (Channel) -> Unit = {}
) {
    var player by mutableStateOf<ExoPlayer?>(null)
        private set
    private var channel = initialChannel
    private var retryToken = 0
    private var generation = 0L
    private var started = false
    private var listener: Player.Listener? = null
    private var savedPosition = 0L
    private var wasLive = true
    private var shouldPlay = true

    fun selectChannel(selected: Channel, retry: Int) {
        if (selected.id == channel.id && selected.streamUrl == channel.streamUrl && retry == retryToken) return
        channel = selected
        retryToken = retry
        savedPosition = 0L
        wasLive = true
        shouldPlay = true
        if (started) playSelection(resume = false)
    }

    fun start() {
        if (started) return
        started = true
        controller.initialize()
        player = checkNotNull(controller.player)
        playSelection(resume = true)
    }

    private fun playSelection(resume: Boolean) {
        val created = player ?: return
        listener?.let(created::removeListener)
        val selected = channel
        val request = ++generation
        val mediaId = "${selected.id}:$request"
        val startedAt = System.nanoTime()
        var firstFrameRecorded = false
        fun isCurrent() = generation == request && player === created &&
            created.currentMediaItem?.mediaId == mediaId
        listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                if (!isCurrent() || firstFrameRecorded) return
                firstFrameRecorded = true
                LoadTimings.recordSince(LoadTimings.Stage.FIRST_FRAME, startedAt)
                onStarted(selected)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (!isCurrent()) return
                onBuffering(selected.id, playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_READY) onError(selected.id, null)
            }

            override fun onPlayerError(error: PlaybackException) {
                if (!isCurrent()) return
                onBuffering(selected.id, false)
                onError(selected.id, "تعذر تشغيل القناة (${error.errorCodeName}).")
            }
        }.also(created::addListener)
        onError(selected.id, null)
        onBuffering(selected.id, true)
        controller.playUrl(selected.streamUrl, mediaId, selected.name)
        if (resume && !wasLive && savedPosition > 0L) created.seekTo(savedPosition)
        created.playWhenReady = if (resume) shouldPlay else true
    }

    fun stop() {
        started = false
        generation++
        player?.let { current ->
            shouldPlay = current.playWhenReady
            savedPosition = current.currentPosition
            wasLive = current.currentTimeline.isEmpty || current.isCurrentMediaItemLive ||
                !current.isCurrentMediaItemSeekable
            listener?.let(current::removeListener)
        }
        listener = null
        player = null
        controller.release()
    }
}