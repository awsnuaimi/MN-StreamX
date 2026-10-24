package com.yourapp.iptv.ui.component.player

import android.view.KeyEvent
import android.view.View
import android.view.SurfaceView
import android.util.TypedValue
import android.util.Log
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.exoplayer.ExoPlayer
import com.yourapp.iptv.domain.model.CaptionSettings
import com.yourapp.iptv.domain.model.CaptionMode
import com.yourapp.iptv.player.caption.CaptionAudioTap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.PlaybackTechnicalInfo
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
    modifier: Modifier = Modifier,
    controllerTimeoutMs: Int = 4500,
    technicalInfoEnabled: Boolean = false,
    onTechnicalInfo: (String, PlaybackTechnicalInfo) -> Unit = { _, _ -> },
    onPlaybackStarted: (Channel) -> Unit = {},
    captionSettings: CaptionSettings = CaptionSettings(),
    onCaptionBinding: (ExoPlayer?, CaptionAudioTap?) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reportError by rememberUpdatedState(onError)
    val reportBuffering by rememberUpdatedState(onBuffering)
    val handleKey by rememberUpdatedState(onRemoteKey)
    val reportControls by rememberUpdatedState(onControllerVisibilityChanged)
    val reportStarted by rememberUpdatedState(onPlaybackStarted)
    val reportCaptionBinding by rememberUpdatedState(onCaptionBinding)
    var renderedChannel by remember { mutableStateOf<Channel?>(null) }
    val audioTap = remember { CaptionAudioTap() }
    val generatedAudio = captionSettings.mode == CaptionMode.GENERATED
    val session = remember(context) {
        PlayerPlaybackSession(PlayerController(context), channel,
            onError = { id, error -> reportError(id, error) },
            onBuffering = { id, loading -> reportBuffering(id, loading) },
            onStarted = {
                Log.i("MNX.FrameCapture", "first video frame rendered")
                renderedChannel = it
                reportStarted(it)
            })
    }

    LaunchedEffect(session, generatedAudio) { session.configureCaptionAudio(if (generatedAudio) audioTap else null) }
    DisposableEffect(session.player, generatedAudio) {
        reportCaptionBinding(session.player, if (generatedAudio) audioTap else null)
        onDispose { reportCaptionBinding(null, null) }
    }

    PlayerTechnicalInfoObserver(if (technicalInfoEnabled) session.player else null, channel.id, onTechnicalInfo)

    // First-frame notifications may arrive before the SurfaceView can be copied.
    // Retry only a few times while the same channel/player is visible; never open a
    // second player or keep a capture job alive after leaving this screen.
    LaunchedEffect(channel.id, channel.streamUrl, retryToken, renderedChannel) {
        val candidate = renderedChannel ?: return@LaunchedEffect
        if (candidate.id != channel.id || candidate.streamUrl != channel.streamUrl) return@LaunchedEffect
        for (attempt in 0 until FrameCapturePolicy.MAX_ATTEMPTS) {
            delay(FrameCapturePolicy.delayBeforeMs(attempt))
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) break
            val currentPlayer = session.player ?: break
            if (currentPlayer.currentMediaItem?.mediaId?.startsWith("${candidate.id}:") != true) break
            val playerView = handle.view
            if (playerView == null || playerView.player == null) {
                Log.i("MNX.FrameCapture", "attempt=${attempt + 1} result=VIEW_NOT_READY")
                continue
            }
            if (playerView.player !== currentPlayer) break
            val source = playerView.videoSurfaceView as? SurfaceView
            val outcome = if (source == null) FrameCaptureResult.SURFACE_NOT_READY
                else LiveFrameCapture.saveIfNeeded(context, candidate, source)
            Log.i("MNX.FrameCapture", "attempt=${attempt + 1} result=$outcome")
            if (!FrameCapturePolicy.continueAfter(outcome, attempt + 1)) break
        }
    }

    LaunchedEffect(channel.id, channel.streamUrl, retryToken) {
        renderedChannel = null
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
                controllerShowTimeoutMs = controllerTimeoutMs.coerceIn(3000, 8000)
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
            val timeout = controllerTimeoutMs.coerceIn(3000, 8000)
            if (view.controllerShowTimeoutMs != timeout) view.controllerShowTimeoutMs = timeout
            view.subtitleView?.apply {
                visibility = if (captionSettings.mode == CaptionMode.EMBEDDED) View.VISIBLE else View.GONE
                setApplyEmbeddedStyles(false)
                setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, captionSettings.textSize.sp.toFloat())
                setStyle(CaptionStyleCompat(android.graphics.Color.WHITE,
                    if (captionSettings.shaded) 0xB0000000.toInt() else android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, android.graphics.Color.BLACK, null))
            }
            view.player = session.player
            view.keepScreenOn = session.player != null
        },
        onReset = null,
        onRelease = handle::releaseView
    )
}