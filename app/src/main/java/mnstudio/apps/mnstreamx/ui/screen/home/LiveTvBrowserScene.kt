package mnstudio.apps.mnstreamx.ui.screen.home

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mnstudio.apps.mnstreamx.domain.model.*
import mnstudio.apps.mnstreamx.ui.presentation.HomeChannelPresentation
import mnstudio.apps.mnstreamx.ui.presentation.HomeTranslationKind
import mnstudio.apps.mnstreamx.ui.screen.parental.LocalParentalSession
import mnstudio.apps.mnstreamx.ui.state.HomeUiState
import mnstudio.apps.mnstreamx.viewmodel.HomeGuideViewModel
import mnstudio.apps.mnstreamx.viewmodel.LiveTvPreferencesViewModel
import mnstudio.apps.mnstreamx.ui.state.LiveTvPreferencesState

@Composable
internal fun LiveTvBrowserScene(state: HomeUiState, appearance: HomeAppearance, labels: HomeLabels,
    onQuery: (String) -> Unit, onGroup: (String?) -> Unit, onPlay: (Channel) -> Unit,
    onFavorite: (Channel) -> Unit, canFavorite: Boolean,
    onPosition: (String, HomeGridPosition) -> Unit, onRestored: (Long) -> Unit,
    modifier: Modifier = Modifier, model: HomeGuideViewModel = hiltViewModel(key = "live-reference-guide"),
    sidebarFocus: FocusRequester? = null, entryFocus: FocusRequester? = null, preferences: LiveTvPreferencesViewModel = hiltViewModel(),
    headerFocus: FocusRequester? = null, aboveHeader: FocusRequester? = null) {
    val personal by preferences.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.browseScope) { preferences.bind(state.browseScope) }
    val scoped = personal.takeIf { it.scope == state.browseScope } ?: LiveTvPreferencesState(state.browseScope)
    val guides by model.uiState.collectAsStateWithLifecycle()
    val access = LocalParentalSession.current.state
    DisposableEffect(model) { onDispose { model.bind(emptyList()) } }
    HomeCaptionHost { captions, setup ->
        LiveTvBrowser(state, appearance, labels, guides, access.epoch, captions, onQuery, onGroup, onPlay,
            onTranslate = { channel ->
                if (HomeChannelPresentation.kind(captions) == HomeTranslationKind.GENERATED) onPlay(channel) else setup()
            }, onFavorite = onFavorite, canFavorite = canFavorite, onPosition = onPosition,
            onRestored = onRestored, onGuideChannels = { model.bind(it, HomeGuideViewModel.MAX_BROWSER_CHANNELS) }, modifier = modifier, sidebarFocus = sidebarFocus, entryFocus = entryFocus,
            preferences = scoped, onPreference = { preferences.change(state.browseScope, it) },
            onPreferencesRetry = { preferences.bind(state.browseScope, retry = true) }, headerEntry = headerFocus, aboveHeader = aboveHeader,
            onWatchChoice = { context, family -> preferences.recordWatch(state.browseScope, context, family) })
    }
}