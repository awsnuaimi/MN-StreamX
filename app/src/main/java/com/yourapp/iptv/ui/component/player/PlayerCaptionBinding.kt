package com.yourapp.iptv.ui.component.player

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.yourapp.iptv.domain.model.CaptionSettings
import com.yourapp.iptv.player.caption.*
import com.yourapp.iptv.viewmodel.CaptionViewModel

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun rememberCaptionBinding(player: ExoPlayer?, audio: CaptionAudioTap?, channelId: String?,
    settings: CaptionSettings, model: CaptionViewModel): EmbeddedCaptions? {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val selection = remember(player) { player?.let(::EmbeddedCaptions) }
    DisposableEffect(selection) { onDispose { selection?.close() } }
    LaunchedEffect(selection, settings.mode, settings.target) { selection?.apply(settings) }
    val epoch by audio?.epoch?.collectAsState() ?: remember { mutableStateOf(0L) }
    DisposableEffect(player, audio, channelId, epoch, lifecycle, model) {
        fun sync() {
            val media = player?.currentMediaItem?.mediaId
            val ready = player?.playWhenReady == true &&
                player.playbackState in listOf(Player.STATE_READY, Player.STATE_BUFFERING) && media != null &&
                channelId != null && media.startsWith("$channelId:") &&
                lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            model.bind(if (ready) audio else null, if (ready) "$media:$epoch" else null)
        }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = sync()
        }
        val observer = LifecycleEventObserver { _, _ -> sync() }
        player?.addListener(listener); lifecycle.addObserver(observer); sync()
        onDispose { player?.removeListener(listener); lifecycle.removeObserver(observer); model.bind(null, null) }
    }
    return selection
}