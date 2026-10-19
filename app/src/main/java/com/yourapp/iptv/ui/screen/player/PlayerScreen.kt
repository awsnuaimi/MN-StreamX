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
import com.yourapp.iptv.ui.state.PlayerEpgUiState
import com.yourapp.iptv.domain.model.PlayerInfoStyle
import com.yourapp.iptv.domain.model.PlaybackTechnicalInfo
import com.yourapp.iptv.viewmodel.PlayerEpgViewModel
import com.yourapp.iptv.viewmodel.PlayerViewModel
import com.yourapp.iptv.viewmodel.PlayerAppearanceViewModel
import kotlinx.coroutines.delay

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    onBack: (String?) -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
    epgViewModel: PlayerEpgViewModel = hiltViewModel(),
    appearanceViewModel: PlayerAppearanceViewModel = hiltViewModel(),
    onAppearance: () -> Unit = {},
    onGuide: (String?, String?, String, Boolean) -> Unit = { _, _, _, _ -> }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteState.collectAsStateWithLifecycle()
    val epg by epgViewModel.uiState.collectAsStateWithLifecycle()
    val appearance by appearanceViewModel.appearance.collectAsStateWithLifecycle()
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
    var focusInfo by remember { mutableStateOf(false) }
    val favoriteFocus = remember { FocusRequester() }
    val detailsFocus = remember { FocusRequester() }
    var detailsOpen by remember(channel?.id) { mutableStateOf(false) }
    var returnToDetails by remember { mutableStateOf(false) }
    var focusEpoch by remember { mutableIntStateOf(0) }
    val surface = remember { PlayerSurfaceHandle() }
    val errorFocus = remember { FocusRequester() }
    val rootFocus = remember { FocusRequester() }
    val error = state.errorMessage ?: playbackError
    var technical by remember(channel?.id, retry) { mutableStateOf(PlaybackTechnicalInfo()) }
    var focusCardId by remember { mutableStateOf<String?>(null) }
    var deckFocusEpoch by remember { mutableIntStateOf(0) }

    fun showInfo() { infoVisible = true; interaction++ }
    fun closeDetails() {
        focusCardId = null
        detailsOpen = false
        returnToDetails = true
        focusInfo = true
        focusEpoch++
        showInfo()
    }
    fun closeChannels() {
        focusCardId = null
        focusInfo = false
        listOpen = false
        focusEpoch++
        showInfo()
    }
    fun openChannels() {
        focusCardId = null
        if (state.queue.size == 0) return
        surface.hideControls()
        controlsVisible = false
        focusInfo = false
        listOpen = true
    }
    fun back() {
        when (playerBackAction(detailsOpen, listOpen, controlsVisible, infoFocused)) {
            PlayerBackAction.CLOSE_DETAILS -> closeDetails()
            PlayerBackAction.CLOSE_CHANNELS -> closeChannels()
            PlayerBackAction.HIDE_CONTROLS -> {
                surface.hideControls()
                controlsVisible = false
                focusEpoch++
            }
            PlayerBackAction.HIDE_INFO -> {
                focusCardId = null
                focusInfo = false
                infoVisible = false
                focusEpoch++
            }
            PlayerBackAction.EXIT -> onBack(channel?.id)
        }
    }
    fun changeChannel(next: Boolean) {
        focusCardId = null
        detailsOpen = false
        returnToDetails = false
        focusInfo = false
        listOpen = false
        surface.hideControls()
        controlsVisible = false
        if (next) viewModel.nextChannel() else viewModel.previousChannel()
        focusEpoch++
        showInfo()
    }
    fun handleRemote(event: KeyEvent, visible: Boolean): Boolean = handlePlayerRemoteKey(
        event.keyCode, event.action, event.repeatCount, visible || infoFocused || detailsOpen, listOpen || detailsOpen
    ) { command ->
        if (detailsOpen) {
            if (command == PlayerRemoteCommand.BACK) closeDetails()
            return@handlePlayerRemoteKey
        }
        when (command) {
            PlayerRemoteCommand.NEXT -> changeChannel(true)
            PlayerRemoteCommand.PREVIOUS -> changeChannel(false)
            PlayerRemoteCommand.TOGGLE_CHANNELS -> if (listOpen) closeChannels() else openChannels()
            PlayerRemoteCommand.SHOW_INFO -> {
                focusCardId = null
                returnToDetails = false
                focusInfo = true
                surface.hideControls()
                controlsVisible = false
                focusEpoch++
                showInfo()
            }
            PlayerRemoteCommand.BACK -> back()
        }
    }

    BackHandler { back() }
    LaunchedEffect(infoVisible, controlsVisible, listOpen) {
        if (!infoVisible || controlsVisible || listOpen) infoFocused = false
    }
    LaunchedEffect(channel) { epgViewModel.selectChannel(channel) }
    LaunchedEffect(channel?.id) { showInfo() }
    LaunchedEffect(channel?.id, interaction, listOpen, detailsOpen, controlsVisible, infoFocused, error, appearance.displaySeconds) {
        if (!listOpen && !detailsOpen && !controlsVisible && !infoFocused && error == null) {
            delay(appearance.timeoutMillis)
            infoVisible = false
        }
    }
    LaunchedEffect(focusEpoch, channel?.id, retry, error, listOpen, detailsOpen) {
        if (!listOpen && !detailsOpen) {
            withFrameNanos { }
            when {
                error != null -> {
                    surface.hideControls()
                    controlsVisible = false
                    runCatching { errorFocus.requestFocus() }
                }
                focusInfo && channel != null -> runCatching {
                    if (appearance.style == PlayerInfoStyle.CARDS && focusCardId == channel.id && state.currentIndex >= 0) {
                        // The rail scrolls to its selected tile before requesting focus.
                    } else if (returnToDetails) detailsFocus.requestFocus() else favoriteFocus.requestFocus()
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
                modifier = Modifier.fillMaxSize(), controllerTimeoutMs = appearance.timeoutMillis.toInt(),
                technicalInfoEnabled = appearance.style == PlayerInfoStyle.CARDS,
                onTechnicalInfo = { id, value -> if (id == activeChannel?.id) technical = value }
            )
        }
        if (infoVisible && !controlsVisible && !listOpen && error == null) {
            PlayerInfoOverlay(channel, state.currentIndex + 1, state.queue.size, state.canChangeChannel,
                onPrevious = { changeChannel(false) }, onNext = { changeChannel(true) },
                onChannels = ::openChannels,
                onControls = { focusCardId = null; focusInfo = false; infoVisible = false; surface.showControls() },
                onBack = { onBack(channel?.id) }, onFocusChanged = { infoFocused = it },
                favoriteFocus = favoriteFocus, favorites = favorites, onToggleFavorite = { focusCardId = null; viewModel.toggleFavorite() },
                onRetryFavorites = viewModel::retryFavorites,
                appearance = appearance, detailsFocus = detailsFocus,
                onAppearance = { focusCardId = null; onAppearance() },
                deckChannels = state.queue.channels, technical = technical, focusCardId = focusCardId, deckFocusEpoch = deckFocusEpoch,
                onGuide = { focusCardId = null; onGuide(channel?.id, state.selectedGroup, state.query, state.favoritesOnly) },
                onSelectDeckChannel = { id ->
                    focusCardId = id; deckFocusEpoch++; focusInfo = true; returnToDetails = false
                    surface.hideControls(); controlsVisible = false
                    viewModel.selectChannel(id); focusEpoch++; showInfo()
                },
                onDetails = { focusCardId = null; detailsOpen = true; surface.hideControls(); controlsVisible = false },
                epg = if (epg.channelId == channel?.id) epg
                    else PlayerEpgUiState(channelId = channel?.id, isLoading = channel != null))
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
            PlayerChannelPanel(state.queue.channels, state.currentIndex, state.selectedGroup, state.query, state.favoritesOnly,
                onSelect = { id -> viewModel.selectChannel(id); closeChannels() },
                onDismiss = ::closeChannels, onRemoteKey = ::handleRemote)
        }
        if (detailsOpen && channel != null) PlayerProgramDetailsDialog(channel.name,
            if (epg.channelId == channel.id) epg else PlayerEpgUiState(channelId = channel.id, isLoading = true),
            appearance, ::closeDetails)
    }
}