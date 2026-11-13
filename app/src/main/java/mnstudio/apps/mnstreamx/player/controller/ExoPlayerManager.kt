package mnstudio.apps.mnstreamx.player.controller

import android.content.Context
import mnstudio.apps.mnstreamx.player.caption.CaptionAudioTap
import mnstudio.apps.mnstreamx.player.caption.CaptionRenderers
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import mnstudio.apps.mnstreamx.domain.performance.LiveStartupTrace
import mnstudio.apps.mnstreamx.domain.performance.PerformanceOutcome
import mnstudio.apps.mnstreamx.domain.performance.PerformanceStage
import java.util.Locale

@androidx.annotation.OptIn(UnstableApi::class)
class ExoPlayerManager(context: Context) {
    private val appContext = context.applicationContext
    private var captionAudio: CaptionAudioTap? = null
    private var trace: LiveStartupTrace? = null
    private var analytics: AnalyticsListener? = null
    fun configureCaptionAudio(tap: CaptionAudioTap?) { captionAudio = tap }
    var player: ExoPlayer? = null
        private set

    fun initialize() {
        if (player != null) return
        val builder = captionAudio?.let { ExoPlayer.Builder(appContext, CaptionRenderers(appContext, it)) }
            ?: ExoPlayer.Builder(appContext)
        player = builder.setLoadControl(LiveLoadControl()).build().apply {
            setAudioAttributes(AudioAttributes.DEFAULT, true)
            setHandleAudioBecomingNoisy(true)
        }
    }

    fun play(url: String, mediaId: String = "", title: String? = null, autoPlay: Boolean = true) {
        initialize()
        cancelStartup()
        captionAudio?.sourceLanguage(null)
        val current = LiveStartupTrace().also { trace = it }
        val uri = Uri.parse(url)
        val output = if (uri.isHierarchical) uri.getQueryParameter("output")?.lowercase(Locale.ROOT) else null
        val item = MediaItem.Builder().setUri(uri).setMediaId(mediaId).apply {
            if (title != null) setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            if (output == "m3u8" || output == "hls") setMimeType(MimeTypes.APPLICATION_M3U8)
        }.build()
        player?.apply {
            analytics?.let(::removeAnalyticsListener)
            analytics = object : AnalyticsListener {
                private fun owns(event: AnalyticsListener.EventTime): Boolean {
                    if (trace !== current || event.windowIndex !in 0 until event.timeline.windowCount) return false
                    return event.timeline.getWindow(event.windowIndex,Timeline.Window()).mediaItem.mediaId == mediaId
                }
                override fun onTracksChanged(eventTime: AnalyticsListener.EventTime, tracks: Tracks) {
                    if (owns(eventTime) && !tracks.isEmpty) {
                        current.reached(PerformanceStage.LIVE_TRACKS_READY)
                        val audio = tracks.groups.firstOrNull { group ->
                            group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO && group.isSelected
                        }
                        val selected = audio?.let { group -> (0 until group.length).firstOrNull(group::isTrackSelected)
                            ?.let { group.getTrackFormat(it).language } }
                        captionAudio?.sourceLanguage(selected)
                    }
                }
                override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
                    if (owns(eventTime) && state == Player.STATE_READY) current.reached(PerformanceStage.LIVE_PLAYER_READY)
                }
                override fun onPlayerError(eventTime: AnalyticsListener.EventTime, error: PlaybackException) {
                    if (owns(eventTime)) current.close(PerformanceOutcome.FAILED)
                }
            }.also(::addAnalyticsListener)
            playWhenReady = autoPlay
            setMediaSource(LiveMediaSourceFactory.create(appContext,current).createMediaSource(item))
            prepare()
        }
    }

    internal fun cancelStartup(outcome: PerformanceOutcome = PerformanceOutcome.CANCELLED) {
        trace?.close(outcome);trace = null
    }

    fun pause() { player?.pause() }
    fun resume() { player?.play() }
    fun stop() { cancelStartup();player?.stop() }
    fun isPlaying(): Boolean = player?.isPlaying == true

    fun release() {
        cancelStartup()
        analytics?.let { player?.removeAnalyticsListener(it) };analytics = null
        player?.release()
        player = null
    }
}