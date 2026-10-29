package com.yourapp.iptv.ui.screen.vod

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.DefaultTrackNameProvider
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import com.yourapp.iptv.domain.model.PlayerAppearance
import com.yourapp.iptv.domain.model.VodSeek
import com.yourapp.iptv.player.vod.*
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.viewmodel.VodPlaybackState
import com.yourapp.iptv.viewmodel.VodPlaybackViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@androidx.annotation.OptIn(UnstableApi::class)
private class VodVideoHandle {
    var view: PlayerView? = null
    fun detach() { view?.apply { player = null; keepScreenOn = false } }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun VodPlayerHost(item: VodPlaybackState.Ready, model: VodPlaybackViewModel,
    labels: VodPlaybackLabels, preferences: VodTrackPreferences, autoNext: Boolean, onBack: () -> Unit,
    appearance: PlayerAppearance = PlayerAppearance()) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val video = remember { VodVideoHandle() }
    val input = remember { VodRemoteInput() }
    val surfaceFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val errorFocus = remember { FocusRequester() }
    var session by remember { mutableStateOf<VodPlaybackSession?>(null) }
    var creationFailed by remember { mutableStateOf(false) }
    val chrome = remember { VodControlsState() }
    var display by rememberSaveable { mutableStateOf(VodDisplayMode.FIT) }
    var seekFeedback by remember { mutableStateOf<Pair<Long,Long>?>(null) }
    var dockFraction by remember { mutableFloatStateOf(.34f) }
    var seekEpoch by remember { mutableIntStateOf(0) }
    var cancelled by rememberSaveable { mutableStateOf(false) }
    var active by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }

    // Creation in this effect lets disposal release the old decoder before creating the next one.
    DisposableEffect(item.url,item.sourceKey) {
        var player: ExoPlayer? = null
        var owned: VodPlaybackSession? = null
        try {
            val created = ExoPlayer.Builder(context.applicationContext)
                .setAudioAttributes(AudioAttributes.DEFAULT,true).setHandleAudioBecomingNoisy(true).build()
            player = created
            preferences.apply(created)
            created.setMediaItem(MediaItem.fromUri(item.url))
            if (item.resumeMs > 0) created.seekTo(item.resumeMs)
            owned = VodPlaybackSession(created,DefaultTrackNameProvider(context.resources),preferences,capture = { position,duration,done ->
                model.capture(item,position,duration,done)
            },clearResume = { duration -> model.restartProgress(item,duration) },diagnosticSource = item.sourceKey)
            session = owned
            created.prepare(); created.playWhenReady = active
        } catch (error: RuntimeException) {
            com.yourapp.iptv.data.diagnostics.DiagnosticFailures.record(com.yourapp.iptv.domain.diagnostics.DiagnosticArea.PLAYBACK,error,item.sourceKey)
            if (owned != null) owned.close() else player?.release()
            session = null; creationFailed = true
        }
        onDispose { video.detach(); owned?.close(); if (session === owned) session = null }
    }
    val current = session
    val snapshot = current?.snapshot ?: VodPlayerSnapshot()
    fun touch() { chrome.touch(); if (snapshot.ended) cancelled = true }
    fun step(delta: Long) {
        touch()
        if (snapshot.seekable && current != null) {
            current.seekBy(delta);seekFeedback = delta to current.snapshot.position;seekEpoch++
        }
    }
    fun open(panel: VodPlayerPanel, target: VodControlTarget) { touch();chrome.open(panel,target) }
    fun closePanel() { chrome.closePanel();input.clear() }
    val tap by rememberUpdatedState(newValue = { touch() })
    fun next() { current?.save(); model.playNext(item) }
    val countdown = vodNextCountdown(snapshot.ended,item.next != null,autoNext,active,
        cancelled || chrome.panel != null || snapshot.failed,::next)
    fun back() {
        if (chrome.panel != null) closePanel()
        else if (countdown != null) { cancelled = true; chrome.touch();chrome.resetFocus() }
        else if (chrome.shown && !snapshot.failed && !creationFailed) chrome.hide()
        else { current?.save(); onBack() }
    }
    BackHandler(onBack = ::back)

    DisposableEffect(current,lifecycle) {
        val observer = LifecycleEventObserver { _,event ->
            when (event) {
                Lifecycle.Event.ON_START -> { active = true; video.view?.player = current?.player }
                Lifecycle.Event.ON_STOP -> {
                    active = false; cancelled = true; chrome.scrubbing = false; chrome.touch(); input.clear(); current?.pause(); video.detach()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(current,lifecycle) {
        val owned = current ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) { owned.tick(chrome.shown); delay(if (chrome.shown) 500L else 1000L) }
        }
    }
    LaunchedEffect(snapshot.ended,snapshot.failed) {
        if (snapshot.ended || snapshot.failed) chrome.touch()
        if (snapshot.failed) { chrome.closePanel();chrome.resetFocus() }
        if (!snapshot.ended) cancelled = false
    }
    LaunchedEffect(chrome.shown,chrome.interaction,snapshot.playing,snapshot.buffering,snapshot.ended,snapshot.failed,
        chrome.panel,chrome.scrubbing,active,appearance.displaySeconds) {
        if (chrome.shown && vodMayHideControls(snapshot,active,chrome.panel != null,chrome.scrubbing)) {
            delay(appearance.timeoutMillis);chrome.hide()
        }
    }
    LaunchedEffect(chrome.shown,current,snapshot.failed) {
        if (current != null && chrome.panel == null) {
            withFrameNanos { }
            if (snapshot.failed) errorFocus.requestFocus()
            else if (!chrome.shown) surfaceFocus.requestFocus()
        }
    }
    LaunchedEffect(seekEpoch) { if (seekFeedback != null) { delay(1000);seekFeedback = null } }
    if (creationFailed) { VodPlaybackFailure(labels.failed(),labels,onBack); return }

    Box(Modifier.fillMaxSize().background(Color.Black).testTag("vod-player")
        .onPreviewKeyEvent { event ->
            val key = event.nativeKeyEvent
            val wasShown = chrome.shown
            if (key.action == android.view.KeyEvent.ACTION_DOWN && wasShown && key.keyCode != android.view.KeyEvent.KEYCODE_BACK) {
                chrome.touch(); if (snapshot.ended) cancelled = true
            }
            input.handle(key.keyCode,key.action,key.repeatCount,wasShown) { command ->
                chrome.touch()
                when (command) {
                    VodRemoteCommand.SHOW -> Unit
                    VodRemoteCommand.REWIND -> step(-VodSeek.STEP_MS)
                    VodRemoteCommand.FORWARD -> step(VodSeek.STEP_MS)
                    VodRemoteCommand.TOGGLE -> current?.toggle()
                    VodRemoteCommand.PLAY -> current?.play()
                    VodRemoteCommand.PAUSE -> current?.pause()
                    VodRemoteCommand.NEXT -> next()
                }
            }
        }.focusRequester(surfaceFocus).focusable().pointerInput(Unit) { detectTapGestures { tap() } }) {
        AndroidView(factory = { owner -> PlayerView(owner).apply {
            video.view = this; useController = false; isFocusable = false; isFocusableInTouchMode = false
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
        } }, update = { view ->
            view.player = if (active) current?.player else null
            view.resizeMode = when (display) {
                VodDisplayMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                VodDisplayMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                VodDisplayMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            }
            view.subtitleView?.setBottomPaddingFraction(if (chrome.shown) dockFraction else .08f)
            view.keepScreenOn = active && snapshot.playWhenReady && !snapshot.ended && !snapshot.failed
        }, onRelease = { view -> view.player = null; view.keepScreenOn = false; if (video.view === view) video.view = null },
            modifier = Modifier.fillMaxSize())
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density,density.fontScale*appearance.textSize.scale)) {
        if (snapshot.buffering && !snapshot.failed && (!chrome.shown || current == null)) Column(Modifier.align(Alignment.Center),horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator();Text(labels.buffering(),color = Color.White)
        }
        if (chrome.shown && current != null) VodPlayerControls(item,snapshot,labels,autoNext,countdown,
            VodPlayerActions(back = { current.save(); onBack() },toggle = { touch(); current.toggle() },
                step = ::step,seek = { touch(); current.seekTo(it) },
                restart = { open(VodPlayerPanel.RESTART,VodControlTarget.RESTART) },
                audio = { open(VodPlayerPanel.AUDIO,VodControlTarget.AUDIO) },
                subtitles = { open(VodPlayerPanel.SUBTITLES,VodControlTarget.SUBTITLES) },next = { touch(); next() },
                automatic = { touch(); cancelled = !it; model.setAutoNext(it) },cancelNext = { touch();cancelled = true;chrome.resetFocus() },retryEpisodes = { touch(); model.retryEpisodes() },
                speed = { open(VodPlayerPanel.SPEED,VodControlTarget.SPEED) },
                display = { open(VodPlayerPanel.DISPLAY,VodControlTarget.DISPLAY) },
                scrubbing = { chrome.scrubbing = it;if (it) touch() }),playFocus,display,chrome.restore,chrome.focusEpoch,
                manageFocus = chrome.panel == null && !snapshot.failed,showHints = appearance.showHints,seekFeedback = seekFeedback,
                onDockFraction = { dockFraction = (it+.02f).coerceIn(.12f,.72f) })
        if (snapshot.failed) Surface(Modifier.align(Alignment.Center).widthIn(max = 620.dp),shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(24.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(labels.failed())
                AppButton(labels.retry(),{ touch(); current?.retry() },Modifier.focusRequester(errorFocus).testTag("vod-retry"))
                AppButton(labels.back(),onBack)
            }
        }
        chrome.panel?.let { panel ->
            if (panel == VodPlayerPanel.AUDIO || panel == VodPlayerPanel.SUBTITLES) {
                val audio = panel == VodPlayerPanel.AUDIO
                VodTrackDialog(if (audio) labels.audio() else labels.subtitles(),
                    if (audio) snapshot.audio else snapshot.subtitles,!audio,labels,
                    onSelect = { current?.selectTrack(if (audio) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT,it);closePanel() },
                    onOff = { current?.disableSubtitles();closePanel() },onClose = ::closePanel)
            } else VodPlayerOptionDialog(panel,labels,snapshot.speed,display,
                onSpeed = { current?.setSpeed(it);closePanel() },onDisplay = { display = it;closePanel() },
                onRestart = { current?.restart();closePanel() },onClose = ::closePanel)
        }
        }
    }
}