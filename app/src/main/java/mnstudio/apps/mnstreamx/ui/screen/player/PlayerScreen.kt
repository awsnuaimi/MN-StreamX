package mnstudio.apps.mnstreamx.ui.screen.player

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import mnstudio.apps.mnstreamx.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import mnstudio.apps.mnstreamx.domain.model.PlaybackTechnicalInfo
import mnstudio.apps.mnstreamx.domain.model.LiveRecoveryState
import mnstudio.apps.mnstreamx.domain.model.Channel
import mnstudio.apps.mnstreamx.domain.profile.ProfileScope
import mnstudio.apps.mnstreamx.ui.component.player.*
import mnstudio.apps.mnstreamx.ui.component.common.FocusInputEpoch
import mnstudio.apps.mnstreamx.ui.component.common.rememberContextFocus
import mnstudio.apps.mnstreamx.ui.state.PlayerEpgUiState
import mnstudio.apps.mnstreamx.ui.screen.history.WatchHistoryDialog
import mnstudio.apps.mnstreamx.viewmodel.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

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
    val parental = mnstudio.apps.mnstreamx.ui.screen.parental.LocalParentalSession.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteState.collectAsStateWithLifecycle()
    val epg by epgViewModel.uiState.collectAsStateWithLifecycle()
    val appearance by appearanceViewModel.appearance.collectAsStateWithLifecycle()
    val channel = state.channel
    val activeChannel by rememberUpdatedState(channel)
    var panels by rememberSaveable(stateSaver = PlayerPanelSaver) { mutableStateOf(PlayerPanelState()) }
    val cardsOpen = panels.panel == PlayerPanel.CARDS
    val browserOpen = cardsOpen || panels.panel == PlayerPanel.CHANNELS
    val recentsOpen = panels.panel == PlayerPanel.RECENTS
    var detailsOpenedFromInfo by rememberSaveable { mutableStateOf(false) }
    var bannerVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var focusEpoch by remember { mutableIntStateOf(0) }
    var focusedId by remember { mutableStateOf<String?>(null) }
    var moreInitial by rememberSaveable { mutableStateOf(PlayerMoreAction.FAVORITE) }
    val panelFocus = rememberContextFocus("live-player")
    var retry by remember(channel?.id, channel?.streamUrl) { mutableIntStateOf(0) }
    var playbackError by remember(channel?.id, channel?.streamUrl, retry) { mutableStateOf<String?>(null) }
    var recoveryState by remember(channel?.id, channel?.streamUrl, retry) { mutableStateOf(LiveRecoveryState()) }
    var buffering by remember(channel?.id, channel?.streamUrl, retry) { mutableStateOf(true) }
    var hasRenderedLiveFrame by remember(channel?.id, channel?.streamUrl, retry) { mutableStateOf(false) }
    var technical by remember(channel?.id, channel?.streamUrl, retry) { mutableStateOf(PlaybackTechnicalInfo()) }
    val error = if (state.sourceChanged) stringResource(R.string.history_source_changed) else state.errorMessage ?: playbackError
    val recovery = if (state.sourceChanged || state.errorMessage != null) LiveRecoveryState() else recoveryState
    val showPlaybackOverlay = error != null || recovery.isRecovering
    val surface = remember { PlayerSurfaceHandle() }
    val captionModel: CaptionViewModel = hiltViewModel()
    val captionState by captionModel.uiState.collectAsStateWithLifecycle()
    var captionPlayer by remember { mutableStateOf<androidx.media3.exoplayer.ExoPlayer?>(null) }
    var captionAudio by remember { mutableStateOf<mnstudio.apps.mnstreamx.player.caption.CaptionAudioTap?>(null) }
    val captionSelection = rememberCaptionBinding(captionPlayer, captionAudio, channel?.id, captionState.settings, captionModel)
    val errorFocus = remember { FocusRequester() }
    val rootFocus = remember { FocusRequester() }
    val activation = remember { PlayerDeckActivation() }
    val remoteKeys = remember { PlayerRemoteKeyGate() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { activation.reset(); remoteKeys.reset(); panelFocus.cancel() }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); activation.reset(); remoteKeys.reset() }
    }
    val focused = remember(browserOpen, state.catalogChannels, focusedId) {
        if (browserOpen && focusedId != null) state.catalogChannels.firstOrNull { it.id == focusedId } else null
    }
    val preview = if (browserOpen) {
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
        if (channel == null || showPlaybackOverlay) return
        viewModel.requestChannels()
        surface.hideControls(); panels = panels.cards(); focusedId = channel.id; focusEpoch++
    }
    fun openMore() {
        viewModel.requestChannels()
        panels = panels.more()
    }
    fun openRecents() {
        if (channel == null || showPlaybackOverlay) return
        surface.hideControls()
        panels = panels.recents()
        bannerVisible = false
    }
    fun openChannels() {
        viewModel.requestChannels()
        surface.hideControls(); panels = panels.channels(); focusedId = null
    }
    fun back() {
        if (panels.panel == PlayerPanel.DETAILS && detailsOpenedFromInfo) {
            detailsOpenedFromInfo = false
            panels = panels.watching()
            focusEpoch++
            return
        }
        when (playerBackAction(panels.panel)) {
            PlayerBackAction.EXIT -> { surface.cancelRecovery(); onBack(channel?.id) }
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
        if (!viewModel.selectBrowserChannel(id)) return
        panels = panels.afterSelection(); focusedId = null
        surface.hideControls()
        focusEpoch++; showBanner()
    }
    fun handleRemote(event: KeyEvent, visible: Boolean): Boolean {
        if (activation.handle(event.keyCode, event.action, event.repeatCount,
                panels.panel == PlayerPanel.WATCHING && !visible && !showPlaybackOverlay, ::showBanner)) return true
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
                PlayerRemoteCommand.OPEN_RECENTS -> openRecents()
                PlayerRemoteCommand.HIDE_CARDS -> hideCards()
                PlayerRemoteCommand.TOGGLE_CHANNELS -> openChannels()
                PlayerRemoteCommand.SHOW_INFO -> {
                    if (channel != null) {
                        surface.hideControls()
                        bannerVisible = false
                        detailsOpenedFromInfo = true
                        panels = panels.watching().details()
                    }
                }
                PlayerRemoteCommand.BACK -> back()
            }
        }
    }

    BackHandler { back() }
    LaunchedEffect(channel) { epgViewModel.selectChannel(channel) }
    LaunchedEffect(browserOpen, focused?.id, channel?.id) {
        previewViewModel.selectChannel(focused?.takeIf { it.id != channel?.id })
    }
    LaunchedEffect(channel?.id) { focusedId = null; showBanner() }
    LaunchedEffect(channel?.id, interaction, panels.panel, showPlaybackOverlay, appearance.displaySeconds) {
        if (panels.panel == PlayerPanel.WATCHING && !showPlaybackOverlay) {
            delay(appearance.timeoutMillis); bannerVisible = false
        }
    }
    val requestInputEpoch = remember(focusEpoch, channel?.id, retry, showPlaybackOverlay, panels.panel) { FocusInputEpoch.value }
    LaunchedEffect(focusEpoch, channel?.id, retry, showPlaybackOverlay, panels.panel) {
        if (!panels.isModal) {
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            withFrameNanos { }
            if (FocusInputEpoch.value != requestInputEpoch || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                return@LaunchedEffect
            when {
                showPlaybackOverlay -> { surface.hideControls(); runCatching { errorFocus.requestFocus() } }
                cardsOpen && channel != null -> Unit
                channel == null -> runCatching { rootFocus.requestFocus() }
                else -> surface.requestFocus()
            }
        }
    }
    CompositionLocalProvider(LocalPlayerFocus provides panelFocus) {
        Box(Modifier.fillMaxSize().background(Color.Black).then(panelFocus.inputModifier())
            .onPreviewKeyEvent { handleRemote(it.nativeKeyEvent, panels.panel == PlayerPanel.CONTROLS) }
            .focusRequester(rootFocus).focusable()) {
            if (channel != null && !state.sourceChanged) PlayerSurface(channel, retry, surface,
                onError = { id, message -> if (id == activeChannel?.id) playbackError = message },
                onBuffering = { id, loading -> if (id == activeChannel?.id) buffering = loading },
                onRemoteKey = ::handleRemote,
                onControllerVisibilityChanged = { visible ->
                    if (visible) panels = panels.controls()
                    else if (panels.panel == PlayerPanel.CONTROLS) { panels = panels.watching(); focusEpoch++ }
                },
                modifier = Modifier.fillMaxSize(), controllerTimeoutMs = appearance.timeoutMillis.toInt(),
                technicalInfoEnabled = true,
                onPlaybackStarted = { selected ->
                    hasRenderedLiveFrame = true
                    viewModel.onPlaybackStarted(selected)
                },
                captionSettings = captionState.settings,
                onCaptionBinding = { player, audio -> captionPlayer = player; captionAudio = audio },
                onRecovery = { id, value -> if (id == activeChannel?.id) recoveryState = value },
                onTechnicalInfo = { id, value -> if (id == activeChannel?.id) technical = value })
            GeneratedCaptionOverlay(captionModel, captionAudio, captionState.settings,
                raised = panels.panel != PlayerPanel.WATCHING || bannerVisible)
            if (bannerVisible && panels.panel == PlayerPanel.WATCHING && !showPlaybackOverlay) {
                PlayerInfoOverlay(channel, state.currentIndex + 1, state.queue.channels, appearance, activeEpg, technical,
                    false, null, PlayerEpgUiState(), focusEpoch, false, ::openMore, ::selectChannel, onFocusChannel = {})
            }
            if (!showPlaybackOverlay && (state.isLoading || (channel != null && buffering))) {
                LiveBufferingIndicator(
                    showImmediately = state.isLoading || !hasRenderedLiveFrame,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            if (showPlaybackOverlay) PlayerErrorOverlay(error, channel != null && !state.sourceChanged,
                state.queue.size > 0 || channel != null, errorFocus,
                onRetry = { surface.cancelRecovery(); retry++; focusEpoch++ }, onChannels = ::openChannels,
                onBack = { surface.cancelRecovery(); onBack(channel?.id) },
                recovery = recovery)
            if (browserOpen) {
                val preferences: LiveTvPreferencesViewModel = hiltViewModel(key = "player-live-browser")
                val personal by preferences.state.collectAsStateWithLifecycle()
                val scope = viewModel.browserScope
                LaunchedEffect(scope) { preferences.bind(scope) }
                val source = ProfileScope.source(scope)
                val access = parental.state
                val ready = source == null || (personal.scope == scope && (personal.ready || personal.error))
                val visible: (Channel) -> Boolean = remember(source, scope, access, personal) {
                    { item ->
                        source == null || (personal.scope == scope && personal.ready && personal.value.visible(item) &&
                            access.profileId == ProfileScope.profile(scope) && access.allows(source, item))
                    }
                }
                val index = remember(state.catalogChannels, channel?.id) {
                    state.catalogChannels.indexOfFirst { it.id == channel?.id }
                }
                PlayerChannelPanel(state.catalogChannels, index, state.favoritesOnly,
                    onSelect = { id ->
                        state.catalogChannels.firstOrNull { it.id == id }?.takeIf(visible)?.let { selectChannel(id) }
                    }, onDismiss = ::back, onRemoteKey = ::handleRemote,
                    loading = state.queueLoading || !ready, organization = state.organization,
                    onMore = if (cardsOpen) ::openMore else null, focusMore = cardsOpen && panels.focusMore,
                    onFocusChannel = { focusedId = it }, previewEpg = if (focused?.id == channel?.id) activeEpg else preview,
                    visible = visible)
            }
            if (recentsOpen) {
                PlayerRecentChannelsRail(
                    playingId = channel?.id,
                    expectedSource = viewModel.browserScope,
                    onDismiss = ::back,
                    onPlay = { selected, source ->
                        // Keep history/profile validation and the established navigation fallback.
                        if (source == viewModel.browserScope) {
                            surface.hideControls()
                            panels = panels.afterSelection()
                            focusEpoch++
                            showBanner()
                            if (!viewModel.selectHistoryChannel(selected, source)) {
                                onOpenHistoryChannel(selected.id, source)
                            }
                        }
                    }
                )
            }
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
                    moreInitial = action
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
                            if (parental.state.allows(mnstudio.apps.mnstreamx.domain.parental.ParentalSection.SETTINGS))
                                panels = panels.captions()
                            else { panels = panels.watching(); parental.model?.requestPin() }
                        }
                        PlayerMoreAction.HISTORY -> panels = panels.history()
                        PlayerMoreAction.DETAILS -> {
                            detailsOpenedFromInfo = false
                            panels = panels.details()
                        }
                        PlayerMoreAction.CHANNELS -> openChannels()
                        PlayerMoreAction.GUIDE -> {
                            panels = panels.watching()
                            onGuide(channel.id, state.selectedGroup, state.query, state.favoritesOnly)
                        }
                        PlayerMoreAction.CONTROLS -> { panels = panels.controls(); surface.showControls() }
                        PlayerMoreAction.APPEARANCE -> onAppearance()
                        PlayerMoreAction.PREVIOUS -> changeChannel(false)
                        PlayerMoreAction.NEXT -> changeChannel(true)
                    }
                }, onRetryFavorites = viewModel::retryFavorites)
            }
            if (panels.panel == PlayerPanel.DETAILS && channel != null)
                PlayerProgramDetailsDialog(channel.name, activeEpg, appearance, ::back, fromInfoKey = detailsOpenedFromInfo)
            if (panels.panel == PlayerPanel.CAPTIONS)
                PlayerCaptionDialog(captionModel, captionSelection, ::back)
            if (panels.panel == PlayerPanel.HISTORY) WatchHistoryDialog(channel?.id, ::back, { selected, source ->
                panels = panels.watching(); focusedId = null; focusEpoch++; showBanner()
                if (!viewModel.selectHistoryChannel(selected, source)) onOpenHistoryChannel(selected.id, source)
            })
        }
    }
}