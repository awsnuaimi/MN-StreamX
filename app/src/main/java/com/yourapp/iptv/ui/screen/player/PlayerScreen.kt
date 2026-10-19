package com.yourapp.iptv.ui.screen.player

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.yourapp.iptv.ui.component.player.PlayerRemoteCommand
import com.yourapp.iptv.ui.component.player.PlayerSurface
import com.yourapp.iptv.ui.component.player.PlayerSurfaceHandle
import com.yourapp.iptv.ui.component.player.handlePlayerRemoteKey
import com.yourapp.iptv.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(onBack: (String?) -> Unit, viewModel: PlayerViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val channel = state.channel
    val activeChannel by rememberUpdatedState(channel)
    var retry by remember(channel?.id) { mutableIntStateOf(0) }
    var playbackError by remember(channel?.id, retry) { mutableStateOf<String?>(null) }
    var buffering by remember(channel?.id, retry) { mutableStateOf(true) }
    var listOpen by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(false) }
    var infoVisible by remember { mutableStateOf(true) }
    var infoFocused by remember { mutableStateOf(false) }
    var interaction by remember { mutableIntStateOf(0) }
    var focusEpoch by remember { mutableIntStateOf(0) }
    val surface = remember { PlayerSurfaceHandle() }
    val errorFocus = remember { FocusRequester() }
    val rootFocus = remember { FocusRequester() }
    val error = state.errorMessage ?: playbackError

    fun showInfo() { infoVisible = true; interaction++ }
    fun closeChannels() {
        listOpen = false
        focusEpoch++
        showInfo()
    }
    fun openChannels() {
        if (state.queue.size == 0) return
        surface.hideControls()
        controlsVisible = false
        listOpen = true
    }
    fun back() {
        when {
            listOpen -> closeChannels()
            controlsVisible -> {
                surface.hideControls()
                controlsVisible = false
                focusEpoch++
            }
            else -> onBack(channel?.id)
        }
    }
    fun changeChannel(next: Boolean) {
        listOpen = false
        surface.hideControls()
        controlsVisible = false
        if (next) viewModel.nextChannel() else viewModel.previousChannel()
        focusEpoch++
        showInfo()
    }
    fun handleRemote(event: KeyEvent, visible: Boolean): Boolean = handlePlayerRemoteKey(
        event.keyCode, event.action, event.repeatCount, visible, listOpen
    ) { command ->
        when (command) {
            PlayerRemoteCommand.NEXT -> changeChannel(true)
            PlayerRemoteCommand.PREVIOUS -> changeChannel(false)
            PlayerRemoteCommand.TOGGLE_CHANNELS -> if (listOpen) closeChannels() else openChannels()
            PlayerRemoteCommand.SHOW_INFO -> {
                surface.hideControls()
                controlsVisible = false
                focusEpoch++
                showInfo()
            }
            PlayerRemoteCommand.BACK -> back()
        }
    }

    BackHandler { back() }
    LaunchedEffect(channel?.id) { showInfo() }
    LaunchedEffect(channel?.id, interaction, listOpen, controlsVisible, infoFocused, error) {
        if (!listOpen && !controlsVisible && !infoFocused && error == null) {
            delay(4500)
            infoVisible = false
        }
    }
    LaunchedEffect(focusEpoch, channel?.id, retry, error, listOpen) {
        if (!listOpen) {
            withFrameNanos { }
            when {
                error != null -> {
                    surface.hideControls()
                    controlsVisible = false
                    runCatching { errorFocus.requestFocus() }
                }
                channel == null -> runCatching { rootFocus.requestFocus() }
                else -> surface.requestFocus()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)
        .onPreviewKeyEvent { handleRemote(it.nativeKeyEvent, controlsVisible) }
        .focusRequester(rootFocus).focusable()) {
        if (channel != null) {
            PlayerSurface(
                channel = channel, retryToken = retry, handle = surface,
                onError = { id, message -> if (id == activeChannel?.id) playbackError = message },
                onBuffering = { id, loading -> if (id == activeChannel?.id) buffering = loading },
                onRemoteKey = ::handleRemote,
                onControllerVisibilityChanged = { controlsVisible = it },
                modifier = Modifier.fillMaxSize()
            )
        }
        if (infoVisible && !controlsVisible && !listOpen && error == null) {
            PlayerInfoOverlay(channel, state.currentIndex + 1, state.queue.size, state.canChangeChannel,
                onPrevious = { changeChannel(false) }, onNext = { changeChannel(true) },
                onChannels = ::openChannels,
                onControls = { infoVisible = false; surface.showControls() },
                onBack = { onBack(channel?.id) }, onFocusChanged = { infoFocused = it })
        }
        if ((state.isLoading || (channel != null && buffering)) && error == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
        if (error != null) {
            PlayerErrorOverlay(error, channel != null, state.queue.size > 0, errorFocus,
                onRetry = { retry++; focusEpoch++ }, onChannels = ::openChannels,
                onBack = { onBack(channel?.id) })
        }
        if (listOpen) {
            PlayerChannelPanel(state.queue.channels, state.currentIndex, state.selectedGroup, state.query,
                onSelect = { id -> viewModel.selectChannel(id); closeChannels() },
                onDismiss = ::closeChannels, onRemoteKey = ::handleRemote)
        }
    }
}