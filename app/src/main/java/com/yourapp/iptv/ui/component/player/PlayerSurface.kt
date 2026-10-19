package com.yourapp.iptv.ui.component.player

import android.view.KeyEvent
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.player.controller.PlayerController

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerSurface(
    channel: Channel,
    retryToken: Int,
    handle: PlayerSurfaceHandle,
    onError: (String, String?) -> Unit,
    onBuffering: (String, Boolean) -> Unit,
    onRemoteKey: (KeyEvent, Boolean) -> Boolean,
    onControllerVisibilityChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reportError by rememberUpdatedState(onError)
    val reportBuffering by rememberUpdatedState(onBuffering)
    val handleKey by rememberUpdatedState(onRemoteKey)
    val reportControls by rememberUpdatedState(onControllerVisibilityChanged)
    val session = remember(context) {
        PlayerPlaybackSession(PlayerController(context), channel,
            onError = { id, error -> reportError(id, error) },
            onBuffering = { id, loading -> reportBuffering(id, loading) })
    }

    LaunchedEffect(channel.id, channel.streamUrl, retryToken) {
        session.selectChannel(channel, retryToken)
    }
    DisposableEffect(session, lifecycle, handle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> session.start()
                Lifecycle.Event.ON_STOP -> {
                    handle.hideControls()
                    handle.detachPlayer()
                    session.stop()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) session.start()
        onDispose {
            lifecycle.removeObserver(observer)
            handle.detachPlayer()
            session.stop()
        }
    }

    AndroidView(
        factory = { viewContext ->
            RemotePlayerView(viewContext).apply {
                handle.view = this
                useController = true
                setControllerAutoShow(false)
                controllerShowTimeoutMs = 4500
                setKeepContentOnPlayerReset(true)
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                this.onRemoteKey = { event, visible -> handleKey(event, visible) }
                setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                    reportControls(visibility == View.VISIBLE)
                })
                isFocusable = true
                isFocusableInTouchMode = true
                requestFocus()
            }
        },
        modifier = modifier,
        update = { view ->
            view.player = session.player
            view.keepScreenOn = session.player != null
        },
        onReset = null,
        onRelease = handle::releaseView
    )
}