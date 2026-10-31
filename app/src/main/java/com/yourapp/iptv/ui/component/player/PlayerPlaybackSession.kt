package com.yourapp.iptv.ui.component.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.LiveRecoveryPhase
import com.yourapp.iptv.domain.model.LiveRecoveryState
import com.yourapp.iptv.player.controller.PlayerController
import com.yourapp.iptv.player.recovery.*
import com.yourapp.iptv.util.LoadTimings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(UnstableApi::class)
internal class PlayerPlaybackSession(
    private val controller: PlayerController,
    initialChannel: Channel,
    private val scope: CoroutineScope,
    private val network: Flow<PlaybackConnection>,
    private val onError: (Channel, String?) -> Unit,
    private val onBuffering: (Channel, Boolean) -> Unit,
    private val onStarted: (Channel, String) -> Unit = { _, _ -> },
    private val onRecovery: (Channel, LiveRecoveryState) -> Unit = { _, _ -> }
) {
    var player by mutableStateOf<ExoPlayer?>(null)
        private set
    private var channel = initialChannel
    private var retryToken = 0
    private var generation = 0L
    private var started = false
    private var listener: Player.Listener? = null
    private var networkJob: Job? = null
    private var savedPosition = 0L
    private var wasLive = true
    private var shouldPlay = true
    private val recovery = LiveRecoveryController(scope,
        onState = { state ->
            if (state.phase == LiveRecoveryPhase.WAITING_NETWORK || state.phase == LiveRecoveryPhase.EXHAUSTED)
                retireAttempt()
            onRecovery(channel, state)
        }, retry = ::retrySelection,
        onTimeout = { if (started) failed(PlaybackException("Live reconnect timeout", null,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)) },
        canRetry = { started && player?.playWhenReady == true })

    private var captionAudio: com.yourapp.iptv.player.caption.CaptionAudioTap? = null
    fun configureCaptionAudio(tap: com.yourapp.iptv.player.caption.CaptionAudioTap?) {
        if (captionAudio === tap) return
        val running = started
        val previousTracks = player?.trackSelectionParameters
        val previousVolume = player?.volume
        val previousSpeed = player?.playbackParameters
        if (running) stop()
        captionAudio = tap
        controller.configureCaptionAudio(tap)
        if (running) {
            start()
            player?.let { current ->
                previousTracks?.let { current.trackSelectionParameters = it }
                previousVolume?.let { current.volume = it }
                previousSpeed?.let { current.playbackParameters = it }
            }
        }
    }

    fun selectChannel(selected: Channel, retry: Int) {
        if (selected.id == channel.id && selected.streamUrl == channel.streamUrl && retry == retryToken) return
        recovery.close()
        channel = selected; retryToken = retry
        savedPosition = 0L; wasLive = true; shouldPlay = true
        recovery.reset()
        if (started) playSelection(resume = false)
    }

    /** Called synchronously before a channel/navigation action can leave a delayed retry behind. */
    fun cancelRecovery() = recovery.close()

    private fun retrySelection() {
        if (!started) return
        if (player?.playWhenReady != true) { recovery.paused(); return }
        playSelection(resume = false, automatic = true)
    }

    fun start() {
        if (started) return
        started = true
        recovery.reset()
        networkJob = scope.launch {
            network.collect { value ->
                recovery.networkChanged(value)
                if (value != PlaybackConnection.OFFLINE && player?.isPlaying == true) recovery.playing(true)
            }
        }
        controller.initialize()
        player = checkNotNull(controller.player)
        playSelection(resume = true)
    }

    private fun playSelection(resume: Boolean, automatic: Boolean = false) {
        val created = player ?: return
        listener?.let(created::removeListener)
        val selected = channel
        val request = ++generation
        val mediaId = "${selected.id}:$request"
        val startedAt = System.nanoTime()
        var firstFrameRecorded = false
        fun isCurrent() = started && generation == request && player === created &&
            created.currentMediaItem?.mediaId == mediaId
        listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                if (!isCurrent() || firstFrameRecorded) return
                firstFrameRecorded = true
                LoadTimings.recordSince(LoadTimings.Stage.FIRST_FRAME, startedAt)
                onStarted(selected, mediaId)
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (!isCurrent()) return
                onBuffering(selected, playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_READY) { recovery.ready(); onError(selected, null) }
                else if (playbackState == Player.STATE_BUFFERING && created.playWhenReady) recovery.buffering()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isCurrent()) recovery.playing(isPlaying)
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (isCurrent() && !playWhenReady) recovery.paused()
            }
            override fun onPlayerError(error: PlaybackException) {
                if (isCurrent()) failed(error)
            }
        }.also(created::addListener)
        if (!automatic) onError(selected, null)
        onBuffering(selected, true)
        try {
            controller.playUrl(selected.streamUrl, mediaId, selected.name)
            if (resume && !wasLive && savedPosition > 0L) created.seekTo(savedPosition)
            created.playWhenReady = if (resume) shouldPlay else true
        } catch (error: CancellationException) {
            throw error
        } catch (error: RuntimeException) {
            failed(PlaybackException("Live player request failed", error, PlaybackException.ERROR_CODE_UNSPECIFIED))
        }
    }

    private fun failed(error: PlaybackException) {
        val created = player ?: return
        if (!started) return
        val wantsPlayback = created.playWhenReady
        retireAttempt()
        com.yourapp.iptv.player.PlaybackDiagnostics.record(error)
        onBuffering(channel, false)
        onError(channel, error.errorCodeName)
        recovery.error(wantsPlayback && LiveRecoveryErrors.retryable(error))
    }

    private fun retireAttempt() {
        val created = player ?: return
        val previous = listener ?: return
        generation++; listener = null
        created.removeListener(previous)
        created.stop()
    }

    fun stop() {
        started = false; generation++
        networkJob?.cancel(); networkJob = null
        recovery.close()
        player?.let { current ->
            shouldPlay = current.playWhenReady
            savedPosition = current.currentPosition
            wasLive = current.currentTimeline.isEmpty || current.isCurrentMediaItemLive || !current.isCurrentMediaItemSeekable
            listener?.let(current::removeListener)
        }
        listener = null; player = null
        controller.release()
    }
}