package mnstudio.apps.mnstreamx.ui.component.player

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.ExoPlayer
import mnstudio.apps.mnstreamx.domain.model.Channel
import mnstudio.apps.mnstreamx.domain.model.LiveRecoveryPhase
import mnstudio.apps.mnstreamx.domain.model.LiveRecoveryState
import mnstudio.apps.mnstreamx.player.PlaybackDiagnostics
import mnstudio.apps.mnstreamx.player.controller.PlayerController
import mnstudio.apps.mnstreamx.player.recovery.LiveRecoveryController
import mnstudio.apps.mnstreamx.player.recovery.LiveRecoveryErrors
import mnstudio.apps.mnstreamx.player.recovery.PlaybackConnection
import mnstudio.apps.mnstreamx.util.LoadTimings
import mnstudio.apps.mnstreamx.domain.performance.PerformanceOutcome
import mnstudio.apps.mnstreamx.domain.performance.LivePlaybackHealthJournal
import mnstudio.apps.mnstreamx.domain.performance.LivePlaybackHealthSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.EOFException

@androidx.annotation.OptIn(UnstableApi::class)
internal class PlayerPlaybackSession(
    private val controller: PlayerController,
    initialChannel: Channel,
    private val scope: CoroutineScope,
    private val network: Flow<PlaybackConnection>,
    private val onError: (Channel, String?) -> Unit,
    private val onBuffering: (Channel, Boolean) -> Unit,
    private val isSelected: (Channel) -> Boolean,
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
    private var permissionListener: Player.Listener? = null
    private var networkJob: Job? = null
    private var progressJob: Job? = null
    private var savedPosition = 0L
    private var wasLive = true
    private var shouldPlay = true
    private var health: LivePlaybackHealthSession? = null
    private val recovery = LiveRecoveryController(
        scope = scope,
        onState = { state ->
            if (state.phase == LiveRecoveryPhase.WAITING_NETWORK) {
                health?.bufferEnded()
                retireAttempt()
                onBuffering(channel, false)
            }
            onRecovery(channel, state)
        },
        retry = ::retrySelection,
        onTimeout = {
            failed(PlaybackException("Live playback stalled", null,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT))
        },
        canRetry = { started && isSelected(channel) && player?.canPlayLive() == true },
        nowMs = SystemClock::elapsedRealtime
    )

    private var captionAudio: mnstudio.apps.mnstreamx.player.caption.CaptionAudioTap? = null
    fun configureCaptionAudio(tap: mnstudio.apps.mnstreamx.player.caption.CaptionAudioTap?) {
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
        health?.finish(); health = null
        channel = selected; retryToken = retry
        savedPosition = 0L; wasLive = true; shouldPlay = true
        recovery.reset()
        if (started) playSelection(resume = false)
    }

    fun cancelRecovery() {
        recovery.close()
        stopProgressMonitor()
    }

    private fun retrySelection() {
        health?.retry()
        playSelection(resume = false, automatic = true)
    }

    fun start() {
        if (started) return
        started = true
        recovery.reset()
        controller.initialize()
        val current = checkNotNull(controller.player)
        player = current
        permissionListener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (!events.containsAny(Player.EVENT_PLAY_WHEN_READY_CHANGED,
                        Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED)) return
                if (!started || this@PlayerPlaybackSession.player !== player || !isSelected(channel)) return
                recovery.playbackPermissionChanged()
                if (!player.canPlayLive()) {
                    onBuffering(channel, false)
                    health?.bufferEnded()
                    return
                }
                if (listener == null) return
                if (player.playbackState == Player.STATE_ENDED) {
                    streamEnded()
                } else if (!player.isPlaying) {
                    if (player.playbackState == Player.STATE_BUFFERING) {
                        onBuffering(channel, true)
                        health?.buffering()
                    }
                    recovery.awaitingPlayback()
                }
            }
        }.also(current::addListener)
        recovery.networkChanged(PlaybackConnection.Unknown)
        networkJob = scope.launch { network.collect(recovery::networkChanged) }
        playSelection(resume = true)
    }

    private fun playSelection(resume: Boolean, automatic: Boolean = false) {
        val created = player ?: return
        if (health == null) health = LivePlaybackHealthJournal.begin()
        listener?.let(created::removeListener)
        listener = null
        stopProgressMonitor()
        val selected = channel
        val request = ++generation
        val playWhenReady = !resume || shouldPlay
        if (!automatic) onError(selected, null)
        created.playWhenReady = playWhenReady
        // Automatic attempts are timed by recovery, including its fallback while marked offline.
        if (!automatic && playWhenReady && recovery.waitIfOffline()) {
            created.stop()
            return
        }
        val mediaId = "${selected.id}:$request"
        val startedAt = System.nanoTime()
        var firstFrameRecorded = false
        var firstProgressReported = false
        fun isCurrent() = started && generation == request && player === created && isSelected(selected) &&
            created.currentMediaItem?.mediaId == mediaId
        listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                if (!isCurrent() || firstFrameRecorded) return
                firstFrameRecorded = true
                health?.firstFrame()
                LoadTimings.recordSince(LoadTimings.Stage.FIRST_FRAME, startedAt)
                onStarted(selected, mediaId)
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (!isCurrent()) return
                val buffering = playbackState == Player.STATE_BUFFERING
                val showBuffering = buffering && created.canPlayLive()
                onBuffering(selected, showBuffering)
                if (showBuffering) health?.buffering() else health?.bufferEnded()
                if (playbackState == Player.STATE_ENDED && created.playWhenReady) {
                    streamEnded()
                } else if (buffering) {
                    recovery.awaitingPlayback()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isCurrent()) return
                stopProgressMonitor()
                if (!isPlaying) {
                    recovery.awaitingPlayback()
                    return
                }

                progressJob = scope.launch {
                    var previousPosition = created.currentPosition
                    var previousVideoCounters: DecoderCounters? = null
                    var previousRenderedFrames = 0
                    while (isCurrent() && created.isPlaying) {
                        delay(PROGRESS_SAMPLE_MS)
                        if (!isCurrent() || !created.isPlaying) break
                        val position = created.currentPosition
                        val tracks = created.currentTracks
                        val videoSelected = tracks.isTypeSelected(C.TRACK_TYPE_VIDEO)
                        val videoCounters = if (videoSelected) created.videoDecoderCounters else null
                        videoCounters?.ensureUpdated()
                        val renderedFrames = videoCounters?.renderedOutputBufferCount ?: 0
                        val previousFrames = if (videoCounters === previousVideoCounters) previousRenderedFrames else 0
                        val videoAdvanced = firstFrameRecorded && renderedFrames > previousFrames
                        val outputAdvanced = if (videoSelected) videoAdvanced else tracks.isTypeSelected(C.TRACK_TYPE_AUDIO)
                        val hasProgress = position > previousPosition && outputAdvanced
                        if (hasProgress) {
                            recovery.playbackAdvanced()
                            if (!firstProgressReported) {
                                firstProgressReported = true
                                onError(selected, null)
                            }
                        } else {
                            recovery.awaitingPlayback()
                        }
                        previousPosition = position
                        previousVideoCounters = videoCounters
                        previousRenderedFrames = renderedFrames
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (isCurrent()) failed(error)
            }
        }.also(created::addListener)
        val showBuffering = created.canPlayLive()
        onBuffering(selected, showBuffering)
        if (showBuffering) health?.buffering()
        try {
            controller.playUrl(selected.streamUrl, mediaId, selected.name, playWhenReady)
            if (isCurrent()) {
                if (resume && !wasLive && savedPosition > 0L) created.seekTo(savedPosition)
                if (created.canPlayLive()) recovery.awaitingPlayback()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: RuntimeException) {
            failed(PlaybackException("Live player request failed", error, PlaybackException.ERROR_CODE_UNSPECIFIED))
        }
    }

    private fun streamEnded() {
        failed(PlaybackException("Live stream ended", EOFException(), PlaybackException.ERROR_CODE_IO_UNSPECIFIED))
    }

    private fun failed(error: PlaybackException) {
        if (player == null || !started || !isSelected(channel)) return
        val decision = LiveRecoveryErrors.classify(error)
        health?.error()
        retireAttempt(PerformanceOutcome.FAILED)
        PlaybackDiagnostics.record(error)
        onBuffering(channel, false)
        onError(channel, error.errorCodeName)
        recovery.error(decision.retryable, decision.retryAfterMs)
    }

    private fun stopProgressMonitor() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun retireAttempt(outcome: PerformanceOutcome = PerformanceOutcome.CANCELLED) {
        stopProgressMonitor()
        val created = player
        val previous = listener
        if (previous != null) {
            generation++
            listener = null
            created?.removeListener(previous)
        }
        controller.retireAttempt(outcome)
        if (previous != null && created != null &&
            created.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS
        ) {
            // ExoPlayer.stop() abandons the focus request, preventing the focus-gain callback.
            created.stop()
        }
    }

    fun stop() {
        health?.finish(); health = null
        started = false; generation++
        networkJob?.cancel(); networkJob = null
        cancelRecovery()
        player?.let { current ->
            shouldPlay = current.playWhenReady
            savedPosition = current.currentPosition
            wasLive = current.currentTimeline.isEmpty || current.isCurrentMediaItemLive || !current.isCurrentMediaItemSeekable
            listener?.let(current::removeListener)
            permissionListener?.let(current::removeListener)
        }
        permissionListener = null
        listener = null; player = null
        controller.release()
    }

    private fun Player.canPlayLive(): Boolean =
        playWhenReady && playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE

    private companion object {
        const val PROGRESS_SAMPLE_MS = 1_000L
    }
}