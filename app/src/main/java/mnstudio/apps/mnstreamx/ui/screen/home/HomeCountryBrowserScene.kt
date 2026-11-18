package mnstudio.apps.mnstreamx.ui.screen.home

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mnstudio.apps.mnstreamx.domain.model.Channel
import mnstudio.apps.mnstreamx.domain.model.HomeAppearance
import mnstudio.apps.mnstreamx.domain.profile.ProfileScope
import mnstudio.apps.mnstreamx.ui.component.common.LocalizedResources
import mnstudio.apps.mnstreamx.ui.screen.parental.LocalParentalSession
import mnstudio.apps.mnstreamx.ui.screen.player.PlayerChannelPanel
import mnstudio.apps.mnstreamx.ui.state.HomeUiState
import mnstudio.apps.mnstreamx.ui.state.LiveTvPreferencesState
import mnstudio.apps.mnstreamx.viewmodel.LiveTvPreferencesViewModel

/** A direct country entry uses the full permitted catalog, independent of the Live-TV filter. */
@Composable
internal fun HomeCountryBrowserScene(catalog: HomeUiState, appearance: HomeAppearance,
    onPlay: (Channel) -> Unit, onDismiss: () -> Unit,
    preferences: LiveTvPreferencesViewModel = hiltViewModel()) {
    val personal by preferences.state.collectAsStateWithLifecycle()
    val scope = catalog.browseScope
    LaunchedEffect(scope) { preferences.bind(scope) }
    val scoped = personal.takeIf { it.scope == scope } ?: LiveTvPreferencesState(scope)
    val access = LocalParentalSession.current.state
    val source = ProfileScope.source(scope)
    val visible: (Channel) -> Boolean = remember(scope, scoped.ready, scoped.value, access) {
        { channel ->
            scoped.ready && source != null && access.profileId == ProfileScope.profile(scope) &&
                access.allows(source, channel) && scoped.value.visible(channel)
        }
    }
    val currentIndex = remember(catalog.channels, catalog.selectedChannelId) {
        catalog.channels.indexOfFirst { it.id == catalog.selectedChannelId }
    }
    LocalizedResources(appearance.language) {
        if (scoped.error && !scoped.ready) {
            CountryPreferencesError(appearance, { preferences.bind(scope, retry = true) }, onDismiss)
        } else {
            PlayerChannelPanel(catalog.channels, currentIndex, favoritesOnly = false,
                onSelect = { id -> catalog.channels.firstOrNull { it.id == id }?.takeIf(visible)?.let(onPlay) },
                onDismiss = onDismiss, onRemoteKey = { _, _ -> false },
                loading = !scoped.ready || source == null || (catalog.isLoading && catalog.channels.isEmpty()),
                organization = catalog.organization, startWithCountries = true, visible = visible)
        }
    }
}

@Composable
private fun CountryPreferencesError(appearance: HomeAppearance, retry: () -> Unit, dismiss: () -> Unit) {
    val labels = remember(appearance.language) { HomeLabels(appearance.language) }
    val retryFocus = remember { FocusRequester() }
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(ready) { if (ready) retryFocus.requestFocus() }
    AlertDialog(onDismissRequest = dismiss, containerColor = liveTvColors(appearance).selected,
        modifier = Modifier.testTag("home-countries-error"),
        title = { Text(labels.section(HomeSection.COUNTRIES)) },
        text = { Text(labels.text("تعذّر تحميل تفضيلات القنوات. أعد المحاولة.",
            "Channel preferences could not be loaded. Please try again.",
            "Sendereinstellungen konnten nicht geladen werden. Bitte erneut versuchen.")) },
        confirmButton = {
            TextButton(onClick = retry, modifier = Modifier.focusRequester(retryFocus)
                .onGloballyPositioned { ready = true }.testTag("home-countries-retry")) {
                Text(labels.text("إعادة المحاولة", "Retry", "Erneut versuchen"))
            }
        }, dismissButton = {
            TextButton(onClick = dismiss, modifier = Modifier.testTag("home-countries-close")) {
                Text(labels.text("إغلاق", "Close", "Schließen"))
            }
        })
}