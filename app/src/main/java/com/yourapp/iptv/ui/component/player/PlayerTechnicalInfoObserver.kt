package com.yourapp.iptv.ui.component.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.yourapp.iptv.domain.model.PlaybackTechnicalInfo

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun PlayerTechnicalInfoObserver(player: ExoPlayer?, channelId: String,
    onInfo: (String, PlaybackTechnicalInfo) -> Unit) {
    val report by rememberUpdatedState(onInfo)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(player, channelId, lifecycle) {
        fun publish() {
            val current = player?.currentMediaItem?.mediaId?.startsWith("$channelId:") == true
            val ready = player?.playbackState == Player.STATE_READY
            val info = if (current && ready && player != null) {
                val video = player.videoFormat
                val size = player.videoSize
                PlaybackTechnicalInfo(
                    width = size.width.takeIf { it > 0 } ?: video?.width ?: 0,
                    height = size.height.takeIf { it > 0 } ?: video?.height ?: 0,
                    frameRate = video?.frameRate,
                    audioChannels = player.audioFormat?.channelCount ?: 0
                )
            } else PlaybackTechnicalInfo()
            report(channelId, info)
        }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) { publish() }
        }
        val subscription = player?.let { current ->
            PlayerInfoLifecycleSubscription(lifecycle,
                connect = { current.addListener(listener); publish() },
                disconnect = { current.removeListener(listener) })
        }
        if (player == null) publish()
        onDispose { subscription?.close() }
    }
}


internal class PlayerInfoLifecycleSubscription(
    private val lifecycle: Lifecycle,
    private val connect: () -> Unit,
    private val disconnect: () -> Unit
) {
    private var connected = false
    private var closed = false
    private val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_DESTROY) detach()
    }
    init {
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            connected = true
            connect()
        }
    }
    private fun detach() {
        if (connected) { connected = false; disconnect() }
    }
    fun close() {
        if (closed) return
        closed = true
        lifecycle.removeObserver(observer)
        detach()
    }
}