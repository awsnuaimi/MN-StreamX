package mnstudio.apps.mnstreamx.player.controller

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

@androidx.annotation.OptIn(UnstableApi::class)
class PlayerController(context: Context) {
    private val manager = ExoPlayerManager(context.applicationContext)
    val player: ExoPlayer? get() = manager.player

    fun configureCaptionAudio(tap: mnstudio.apps.mnstreamx.player.caption.CaptionAudioTap?) = manager.configureCaptionAudio(tap)
    fun initialize() = manager.initialize()
    fun playUrl(url: String, mediaId: String = "", title: String? = null, playWhenReady: Boolean = true) =
        manager.play(url, mediaId, title, playWhenReady)
    fun pause() = manager.pause()
    fun resume() = manager.resume()
    fun stop() = manager.stop()
    internal fun retireAttempt(outcome: mnstudio.apps.mnstreamx.domain.performance.PerformanceOutcome =
        mnstudio.apps.mnstreamx.domain.performance.PerformanceOutcome.CANCELLED) = manager.retireAttempt(outcome)
    fun release() = manager.release()
    fun isPlaying(): Boolean = manager.isPlaying()
}