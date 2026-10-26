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
    labels: VodPlaybackLabels, preferences: VodTrackPreferences, autoNext: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val video = remember { VodVideoHandle() }
    val input = remember { VodRemoteInput() }
    val surfaceFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val errorFocus = remember { FocusRequester() }
    var session by remember { mutableStateOf<VodPlaybackSession?>(null) }
    var creationFailed by remember { mutableStateOf(false) }
    var controls by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var trackType by remember { mutableStateOf<Int?>(null) }
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
    fun touch() { interaction++; controls = true; if (snapshot.ended) cancelled = true }
    val tap by rememberUpdatedState(newValue = { touch() })
    fun next() { current?.save(); model.playNext(item) }
    val countdown = vodNextCountdown(snapshot.ended,item.next != null,autoNext,active,
        cancelled || trackType != null || snapshot.failed,::next)
    fun back() {
        if (countdown != null) { cancelled = true; controls = true }
        else if (controls && !snapshot.failed && !creationFailed) controls = false
        else { current?.save(); onBack() }
    }
    BackHandler(onBack = ::back)

    DisposableEffect(current,lifecycle) {
        val observer = LifecycleEventObserver { _,event ->
            when (event) {
                Lifecycle.Event.ON_START -> { active = true; video.view?.player = current?.player }
                Lifecycle.Event.ON_STOP -> {
                    active = false; cancelled = true; input.clear(); current?.pause(); video.detach()
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
            while (isActive) { owned.tick(controls); delay(if (controls) 500L else 1000L) }
        }
    }
    LaunchedEffect(snapshot.ended,snapshot.failed) {
        if (snapshot.ended || snapshot.failed) controls = true
        if (!snapshot.ended) cancelled = false
    }
    LaunchedEffect(controls,snapshot.playing,snapshot.ended,snapshot.failed,trackType,interaction,active) {
        if (controls && snapshot.playing && !snapshot.ended && !snapshot.failed && trackType == null && active) {
            delay(5000); controls = false
        }
    }
    LaunchedEffect(controls,trackType,current,snapshot.failed) {
        if (current != null && trackType == null) {
            withFrameNanos { }
            if (snapshot.failed) errorFocus.requestFocus()
            else if (controls) playFocus.requestFocus() else surfaceFocus.requestFocus()
        }
    }
    if (creationFailed) { VodPlaybackFailure(labels.failed(),labels,onBack); return }

    Box(Modifier.fillMaxSize().background(Color.Black).testTag("vod-player")
        .onPreviewKeyEvent { event ->
            val key = event.nativeKeyEvent
            if (key.action == android.view.KeyEvent.ACTION_DOWN) {
                interaction++; if (snapshot.ended) cancelled = true
            }
            input.handle(key.keyCode,key.action,key.repeatCount,controls) { command ->
                controls = true
                when (command) {
                    VodRemoteCommand.SHOW -> Unit
                    VodRemoteCommand.REWIND -> current?.seekBy(-VodSeek.STEP_MS)
                    VodRemoteCommand.FORWARD -> current?.seekBy(VodSeek.STEP_MS)
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
            view.keepScreenOn = active && snapshot.playWhenReady && !snapshot.ended && !snapshot.failed
        }, onRelease = { view -> view.player = null; view.keepScreenOn = false; if (video.view === view) video.view = null },
            modifier = Modifier.fillMaxSize())
        if (snapshot.buffering && !snapshot.failed) CircularProgressIndicator(Modifier.align(Alignment.Center))
        if (controls && current != null) VodPlayerControls(item,snapshot,labels,autoNext,countdown,
            VodPlayerActions(back = { current.save(); onBack() },toggle = { touch(); current.toggle() },
                step = { touch(); current.seekBy(it) },seek = { touch(); current.seekTo(it) },
                restart = { touch(); current.restart() },audio = { touch(); trackType = C.TRACK_TYPE_AUDIO },
                subtitles = { touch(); trackType = C.TRACK_TYPE_TEXT },next = { touch(); next() },
                automatic = { touch(); cancelled = !it; model.setAutoNext(it) },cancelNext = { cancelled = true },retryEpisodes = { touch(); model.retryEpisodes() }),playFocus)
        if (snapshot.failed) Surface(Modifier.align(Alignment.Center).widthIn(max = 620.dp),shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(24.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(labels.failed())
                AppButton(labels.retry(),{ touch(); current?.retry() },Modifier.focusRequester(errorFocus).testTag("vod-retry"))
                AppButton(labels.back(),onBack)
            }
        }
        trackType?.let { type -> VodTrackDialog(if (type == C.TRACK_TYPE_AUDIO) labels.audio() else labels.subtitles(),
            if (type == C.TRACK_TYPE_AUDIO) snapshot.audio else snapshot.subtitles,type == C.TRACK_TYPE_TEXT,labels,
            onSelect = { current?.selectTrack(type,it); trackType = null },
            onOff = { current?.disableSubtitles(); trackType = null },onClose = { trackType = null }) }
    }
}