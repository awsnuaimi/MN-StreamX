package com.yourapp.iptv.ui.screen.vod

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import com.yourapp.iptv.viewmodel.VodPlaybackState
import com.yourapp.iptv.viewmodel.VodPlaybackViewModel

@Composable
fun VodPlaybackScreen(onBack: () -> Unit, model: VodPlaybackViewModel = hiltViewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    when (val value = state) {
        VodPlaybackState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        VodPlaybackState.Error -> PlaybackFailure(onBack)
        is VodPlaybackState.Ready -> VodPlaybackPlayer(value, model, onBack)
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VodPlaybackPlayer(value: VodPlaybackState.Ready, model: VodPlaybackViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var failed by remember(value.url) { mutableStateOf(false) }
    val player = remember(value.url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(value.url))
            if (value.resumeMs > 0L) seekTo(value.resumeMs)
            prepare()
            playWhenReady = true
        }
    }
    LaunchedEffect(player) {
        while (true) {
            delay(10_000L)
            if (player.playbackState == Player.STATE_READY && player.isPlaying) {
                model.capture(player.currentPosition, player.duration)
            }
        }
    }
    DisposableEffect(player, lifecycle) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { failed = true }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) failed = false
                if (playbackState == Player.STATE_ENDED) {
                    model.capture(player.currentPosition, player.duration, completed = true)
                }
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                model.capture(player.currentPosition, player.duration,
                    completed = player.playbackState == Player.STATE_ENDED)
                player.pause()
            }
        }
        player.addListener(listener)
        lifecycle.addObserver(observer)
        onDispose {
            model.capture(player.currentPosition, player.duration,
                completed = player.playbackState == Player.STATE_ENDED)
            lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
        }
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { ctx ->
            PlayerView(ctx).apply {
                useController = true
                setControllerAutoShow(true)
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                this.player = player
                isFocusable = true
                isFocusableInTouchMode = true
                requestFocus()
            }
        }, update = { it.player = player }, modifier = Modifier.fillMaxSize())
        if (failed) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Playback failed / Wiedergabe fehlgeschlagen / تعذر التشغيل")
            Button(onClick = onBack) { Text("Back / Zurück / رجوع") }
        }
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(6.dp)) {
            Text("‹ ${value.title}")
        }
    }
}

@Composable
private fun PlaybackFailure(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Unable to play this video / Wiedergabe nicht möglich / تعذر تشغيل الفيديو")
            Button(onClick = onBack) { Text("Back / Zurück / رجوع") }
        }
    }
}