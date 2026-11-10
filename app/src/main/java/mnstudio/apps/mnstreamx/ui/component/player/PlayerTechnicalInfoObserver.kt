package mnstudio.apps.mnstreamx.ui.component.player

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
import mnstudio.apps.mnstreamx.domain.model.PlaybackTechnicalInfo

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun PlayerTechnicalInfoObserver(player: ExoPlayer?, channelId: String,
    onInfo: (String, PlaybackTechnicalInfo) -> Unit) {
    val report by rememberUpdatedState(onInfo)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(player, channelId, lifecycle) {
        val emissions = TechnicalInfoEmissionGate()
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
            if (emissions.accept(info)) report(channelId, info)
        }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) ||
                    events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                    events.contains(Player.EVENT_VIDEO_SIZE_CHANGED) ||
                    events.contains(Player.EVENT_TRACKS_CHANGED)) {
                    publish()
                }
            }
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

/**
 * Owns exactly one listener connection while its screen is STARTED.
 * A STOP/START round-trip must reconnect, but disposal is terminal.
 */
internal class PlayerInfoLifecycleSubscription(
    private val lifecycle: Lifecycle,
    private val connect: () -> Unit,
    private val disconnect: () -> Unit
) {
    private var connected = false
    private var closed = false
    private val observer = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> attach()
            Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY -> detach()
            else -> Unit
        }
    }

    init {
        lifecycle.addObserver(observer)
        // addObserver may synchronously deliver ON_START; attach() is idempotent.
        attach()
    }

    private fun attach() {
        if (closed || connected || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        connect()
        connected = true
    }

    private fun detach() {
        if (!connected) return
        connected = false
        disconnect()
    }

    fun close() {
        if (closed) return
        closed = true
        lifecycle.removeObserver(observer)
        detach()
    }
}
/** Scoped to one player and channel; avoid redundant Compose state writes on Media3 events. */
internal class TechnicalInfoEmissionGate {
    private var last: PlaybackTechnicalInfo? = null
    fun accept(value: PlaybackTechnicalInfo): Boolean {
        if (value == last) return false
        last = value
        return true
    }
}