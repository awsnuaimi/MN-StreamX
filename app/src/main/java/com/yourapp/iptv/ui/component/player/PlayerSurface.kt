package com.yourapp.iptv.ui.component.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.yourapp.iptv.player.controller.PlayerController

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerSurface(
    url: String,
    onError: (String) -> Unit,
    onBuffering: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val appContext = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val controller = remember(appContext) { PlayerController(appContext) }
    val holder = remember { ViewHolder() }
    val reportError by rememberUpdatedState(onError)
    val reportBuffering by rememberUpdatedState(onBuffering)
    var player by remember { mutableStateOf<ExoPlayer?>(null) }

    DisposableEffect(controller, lifecycle, url) {
        var shouldPlay = true
        var savedPosition = 0L
        var wasLive = true
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                reportBuffering(playbackState == Player.STATE_BUFFERING)
            }
            override fun onPlayerError(error: PlaybackException) {
                reportBuffering(false)
                reportError("تعذر تشغيل القناة (${error.errorCodeName}).")
            }
        }
        fun start() {
            if (controller.player != null) return
            controller.initialize()
            val created = checkNotNull(controller.player)
            created.addListener(listener)
            controller.playUrl(url)
            if (!wasLive && savedPosition > 0) created.seekTo(savedPosition)
            created.playWhenReady = shouldPlay
            player = created
        }
        fun stop() {
            controller.player?.let {
                shouldPlay = it.playWhenReady
                savedPosition = it.currentPosition
                wasLive = it.isCurrentMediaItemLive
                it.removeListener(listener)
            }
            holder.view?.player = null
            holder.view?.keepScreenOn = false
            player = null
            controller.release()
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        onDispose {
            lifecycle.removeObserver(observer)
            stop()
        }
    }

    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                holder.view = this
                useController = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                isFocusable = true
                isFocusableInTouchMode = true
                requestFocus()
            }
        },
        modifier = modifier,
        update = { view ->
            view.player = player
            view.keepScreenOn = player != null
        },
        onReset = null,
        onRelease = { view ->
            view.player = null
            view.keepScreenOn = false
            if (holder.view === view) holder.view = null
        }
    )
}

private class ViewHolder { var view: PlayerView? = null }