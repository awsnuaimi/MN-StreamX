package com.yourapp.iptv.ui.screen.player

import com.yourapp.iptv.ui.component.organization.organizationFilterName
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import com.yourapp.iptv.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.yourapp.iptv.domain.model.PlaybackTechnicalInfo
import com.yourapp.iptv.ui.component.player.*
import com.yourapp.iptv.ui.state.PlayerEpgUiState
import com.yourapp.iptv.ui.screen.history.WatchHistoryDialog
import com.yourapp.iptv.viewmodel.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    onBack: (String?) -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
    epgViewModel: PlayerEpgViewModel = hiltViewModel(),
    appearanceViewModel: PlayerAppearanceViewModel = hiltViewModel(),
    onAppearance: () -> Unit = {},
    onGuide: (String?, String?, String, Boolean) -> Unit = { _, _, _, _ -> },
    previewViewModel: PlayerEpgViewModel = hiltViewModel(key = "deck-preview-epg"),
    homeAppearanceViewModel: HomeAppearanceViewModel = hiltViewModel(),
    onOpenHistoryChannel: (String, String) -> Unit = { _, _ -> }
) {
    val home by homeAppearanceViewModel.appearance.collectAsStateWithLifecycle()
    val language = home?.language
    if (language == null) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    LocalizedPlayer(language) {
        PlayerScreenContent(onBack, viewModel, epgViewModel, appearanceViewModel, onAppearance, onGuide,
            previewViewModel, onOpenHistoryChannel)
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun PlayerScreenContent(onBack: (String?) -> Unit, viewModel: PlayerViewModel,
    epgViewModel: PlayerEpgViewModel, appearanceViewModel: PlayerAppearanceViewModel,
    onAppearance: () -> Unit, onGuide: (String?, String?, String, Boolean) -> Unit,
    previewViewModel: PlayerEpgViewModel, onOpenHistoryChannel: (String, String) -> Unit) {
    val parental = com.yourapp.iptv.ui.screen.parental.LocalParentalSession.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteState.collectAsStateWithLifecycle()
    val epg by epgViewModel.uiState.collectAsStateWithLifecycle()
    val appearance by appearanceViewModel.appearance.collectAsStateWithLifecycle()
    val channel = state.channel
    val activeChannel by rememberUpdatedState(channel)
    var panels by remember { mutableStateOf(PlayerPanelState()) }
    val cardsOpen = panels.panel == PlayerPanel.CARDS
    var bannerVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var focusEpoch by remember { mutableIntStateOf(0) }
    var focusedId by remember { mutableStateOf<String?>(null) }
    var moreInitial by remember { mutableStateOf(PlayerMoreAction.FAVORITE) }
    var retry by remember(channel?.id) { mutableIntStateOf(0) }
    var playbackError by remember(channel?.id, retry) { mutableStateOf<String?>(null) }
    var buffering by remember(channel?.id, retry) { mutableStateOf(true) }
    var technical by remember(channel?.id, retry) { mutableStateOf(PlaybackTechnicalInfo()) }
    val error = if (state.sourceChanged) stringResource(R.string.history_source_changed) else state.errorMessage ?: playbackError
    val surface = remember { PlayerSurfaceHandle() }
    val captionModel: CaptionViewModel = hiltViewModel()
    val captionState by captionModel.uiState.collectAsStateWithLifecycle()
    var captionPlayer by remember { mutableStateOf<androidx.media3.exoplayer.ExoPlayer?>(null) }
    var captionAudio by remember { mutableStateOf<com.yourapp.iptv.player.caption.CaptionAudioTap?>(null) }
    val captionSelection = rememberCaptionBinding(captionPlayer, captionAudio, channel?.id, captionState.settings, captionModel)
    val errorFocus = remember { FocusRequester() }
    val rootFocus = remember { FocusRequester() }
    val activation = remember { PlayerDeckActivation() }
    val remoteKeys = remember { PlayerRemoteKeyGate() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { activation.reset(); remoteKeys.reset() }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); activation.reset(); remoteKeys.reset() }
    }
    val focused = if (cardsOpen) state.queue.channels.firstOrNull { it.id == focusedId } else null
    val preview = if (cardsOpen) {
        val value by previewViewModel.uiState.collectAsStateWithLifecycle()
        value
    } else PlayerEpgUiState()
    val activeEpg = if (epg.channelId == channel?.id) epg
        else PlayerEpgUiState(channelId = channel?.id, isLoading = channel != null)

    fun showBanner() { bannerVisible = true; interaction++ }
    fun hideCards() {
        panels = panels.watching(); focusedId = null; bannerVisible = false; focusEpoch++
    }
    fun openCards() {
        if (channel == null || error != null) return
        surface.hideControls(); panels = panels.cards(); focusedId = channel.id; focusEpoch++
    }
    fun openMore() {
        moreInitial = PlayerMoreAction.FAVORITE; panels = panels.more()
    }
    fun openChannels() {
        surface.hideControls(); panels = panels.channels(); focusedId = null
    }
    fun back() {
        when (playerBackAction(panels.panel)) {
            PlayerBackAction.EXIT -> onBack(channel?.id)
            PlayerBackAction.HIDE_CARDS -> hideCards()
            else -> {
                if (panels.panel == PlayerPanel.CONTROLS) surface.hideControls()
                if (panels.panel == PlayerPanel.DETAILS) moreInitial = PlayerMoreAction.DETAILS
                if (panels.panel == PlayerPanel.HISTORY) moreInitial = PlayerMoreAction.HISTORY
                if (panels.panel == PlayerPanel.CAPTIONS) moreInitial = PlayerMoreAction.CAPTIONS
                panels = panels.closeTop(); focusedId = null; focusEpoch++
            }
        }
    }
    fun changeChannel(next: Boolean) {
        panels = panels.afterZapping(); focusedId = null
        surface.hideControls()
        if (next) viewModel.nextChannel() else viewModel.previousChannel()
        focusEpoch++; showBanner()
    }
    fun selectChannel(id: String) {
        panels = panels.afterSelection(); focusedId = null
        surface.hideControls()
        // Selecting the active channel only closes the rail; the stream is left running.
        if (id != channel?.id) viewModel.selectChannel(id)
        focusEpoch++; showBanner()
    }
    fun handleRemote(event: KeyEvent, visible: Boolean): Boolean {
        if (activation.handle(event.keyCode, event.action, event.repeatCount,
                panels.panel == PlayerPanel.WATCHING && !visible && error == null, ::showBanner)) return true
        return remoteKeys.handle(event.keyCode, event.action, event.repeatCount,
            visible || panels.isModal, panels.isModal, cardsOpen) { command ->
            if (panels.isModal) {
                if (command == PlayerRemoteCommand.BACK) back()
                else if (panels.panel == PlayerPanel.CHANNELS && command == PlayerRemoteCommand.TOGGLE_CHANNELS) back()
                return@handle
            }
            when (command) {
                PlayerRemoteCommand.NEXT -> changeChannel(true)
                PlayerRemoteCommand.PREVIOUS -> changeChannel(false)
                PlayerRemoteCommand.OPEN_CARDS -> openCards()
                PlayerRemoteCommand.HIDE_CARDS -> hideCards()
                PlayerRemoteCommand.TOGGLE_CHANNELS -> openChannels()
                PlayerRemoteCommand.SHOW_INFO -> {
                    surface.hideControls()
                    if (panels.panel == PlayerPanel.CONTROLS) panels = panels.watching()
                    showBanner()
                }
                PlayerRemoteCommand.BACK -> back()
            }
        }
    }

    BackHandler { back() }
    LaunchedEffect(channel) { epgViewModel.selectChannel(channel) }
    LaunchedEffect(cardsOpen, focused?.id, channel?.id) {
        previewViewModel.selectChannel(focused?.takeIf { cardsOpen && it.id != channel?.id })
    }
    LaunchedEffect(channel?.id) { focusedId = null; showBanner() }
    LaunchedEffect(channel?.id, interaction, panels.panel, error, appearance.displaySeconds) {
        if (panels.panel == PlayerPanel.WATCHING && error == null) {
            delay(appearance.timeoutMillis); bannerVisible = false
        }
    }
    LaunchedEffect(focusEpoch, channel?.id, retry, error, panels.panel) {
        if (!panels.isModal) {
            withFrameNanos { }
            when {
                error != null -> { surface.hideControls(); runCatching { errorFocus.requestFocus() } }
                cardsOpen && channel != null -> Unit // Rail restores focus after scrolling.
                channel == null -> runCatching { rootFocus.requestFocus() }
                else -> surface.requestFocus()
            }
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)
        .onPreviewKeyEvent { handleRemote(it.nativeKeyEvent, panels.panel == PlayerPanel.CONTROLS) }
        .focusRequester(rootFocus).focusable()) {
        if (channel != null) PlayerSurface(channel, retry, surface,
            onError = { id, message -> if (id == activeChannel?.id) playbackError = message },
            onBuffering = { id, loading -> if (id == activeChannel?.id) buffering = loading },
            onRemoteKey = ::handleRemote,
            onControllerVisibilityChanged = { visible ->
                if (visible) panels = panels.controls()
                else if (panels.panel == PlayerPanel.CONTROLS) { panels = panels.watching(); focusEpoch++ }
            },
            modifier = Modifier.fillMaxSize(), controllerTimeoutMs = appearance.timeoutMillis.toInt(),
            technicalInfoEnabled = true,
            onPlaybackStarted = viewModel::onPlaybackStarted,
            captionSettings = captionState.settings,
            onCaptionBinding = { player, audio -> captionPlayer = player; captionAudio = audio },
            onTechnicalInfo = { id, value -> if (id == activeChannel?.id) technical = value })
        GeneratedCaptionOverlay(captionModel, captionAudio, captionState.settings,
            raised = panels.panel != PlayerPanel.WATCHING || bannerVisible)
        if ((cardsOpen || (bannerVisible && panels.panel == PlayerPanel.WATCHING)) && error == null) {
            PlayerInfoOverlay(channel, state.currentIndex + 1, state.queue.channels, appearance, activeEpg, technical,
                cardsOpen, focused, preview, focusEpoch, panels.focusMore, ::openMore, ::selectChannel,
                onFocusChannel = { focusedId = it })
        }
        if ((state.isLoading || (channel != null && buffering)) && error == null)
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        if (error != null) PlayerErrorOverlay(error, channel != null, state.queue.size > 0, errorFocus,
            onRetry = { retry++; focusEpoch++ }, onChannels = ::openChannels, onBack = { onBack(channel?.id) })
        if (panels.panel == PlayerPanel.CHANNELS) PlayerChannelPanel(state.queue.channels, state.currentIndex,
            organizationFilterName(state.selectedGroup, state.organization), state.query, state.favoritesOnly, onSelect = ::selectChannel,
            onDismiss = ::back, onRemoteKey = ::handleRemote)
        if (panels.panel == PlayerPanel.MORE && channel != null) {
        val lastChannel by viewModel.lastChannelState.collectAsStateWithLifecycle()
        val recallScope = rememberCoroutineScope()
        DisposableEffect(viewModel, lifecycle) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START) viewModel.showLastChannelMenu()
                if (event == Lifecycle.Event.ON_STOP) viewModel.hideLastChannelMenu()
            }
            lifecycle.addObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) viewModel.showLastChannelMenu()
            onDispose { lifecycle.removeObserver(observer); viewModel.hideLastChannelMenu() }
        }
        PlayerMoreDialog(channel, appearance, favorites,
            state.canChangeChannel, moreInitial, onRemoteKey = ::handleRemote, onDismiss = ::back,
            lastChannel = lastChannel, onRetryLastChannel = viewModel::retryLastChannel,
            onAction = { action ->
                when (action) {
                    PlayerMoreAction.FAVORITE -> viewModel.toggleFavorite()
                    PlayerMoreAction.LAST_CHANNEL -> recallScope.launch {
                        val target = viewModel.recallLastChannel() ?: return@launch
                        surface.hideControls(); panels = panels.watching()
                        focusedId = null; focusEpoch++; showBanner()
                        if (!viewModel.selectHistoryChannel(target.channel, target.sourceKey))
                            onOpenHistoryChannel(target.channel.id, target.sourceKey)
                    }
                    PlayerMoreAction.CAPTIONS -> {
                        if (parental.state.allows(com.yourapp.iptv.domain.parental.ParentalSection.SETTINGS))
                            panels = panels.captions()
                        else { panels = panels.watching(); parental.model?.requestPin() }
                    }
                    PlayerMoreAction.HISTORY -> panels = panels.history()
                    PlayerMoreAction.DETAILS -> panels = panels.details()
                    PlayerMoreAction.CHANNELS -> openChannels()
                    PlayerMoreAction.GUIDE -> {
                        panels = panels.watching()
                        onGuide(channel.id, state.selectedGroup, state.query, state.favoritesOnly)
                    }
                    PlayerMoreAction.CONTROLS -> { panels = panels.controls(); surface.showControls() }
                    PlayerMoreAction.APPEARANCE -> { panels = panels.watching(); onAppearance() }
                    PlayerMoreAction.PREVIOUS -> changeChannel(false)
                    PlayerMoreAction.NEXT -> changeChannel(true)
                }
            }, onRetryFavorites = viewModel::retryFavorites)
        }
        if (panels.panel == PlayerPanel.DETAILS && channel != null)
            PlayerProgramDetailsDialog(channel.name, activeEpg, appearance, ::back)
        if (panels.panel == PlayerPanel.CAPTIONS)
            PlayerCaptionDialog(captionModel, captionSelection, ::back)
        if (panels.panel == PlayerPanel.HISTORY) WatchHistoryDialog(channel?.id, ::back, { selected, source ->
            panels = panels.watching(); focusedId = null; focusEpoch++; showBanner()
            if (!viewModel.selectHistoryChannel(selected, source)) onOpenHistoryChannel(selected.id, source)
        })
    }
}