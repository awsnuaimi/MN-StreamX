package com.yourapp.iptv.ui.screen.home

import androidx.compose.ui.res.stringResource
import com.yourapp.iptv.R
import androidx.activity.compose.BackHandler
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourapp.iptv.ui.component.channel.FavoriteFeedback
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.viewmodel.HomeViewModel
import com.yourapp.iptv.util.LoadTimings
import com.yourapp.iptv.viewmodel.HomeAppearanceViewModel
import com.yourapp.iptv.viewmodel.HomeQuickAccessViewModel
import com.yourapp.iptv.domain.model.HomeDashboardSelection
import com.yourapp.iptv.domain.parental.ParentalSection
import com.yourapp.iptv.ui.screen.parental.LocalParentalSession
import com.yourapp.iptv.ui.screen.parental.ParentalHomeControl

@Composable
fun HomeScreen(
    onSettings: () -> Unit,
    onPlayChannel: (String, String?, String, Boolean) -> Unit,
    onOpenGuide: (String?, String?, String, Boolean) -> Unit,
    returnedChannelId: String? = null,
    onReturnHandled: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    appearanceViewModel: HomeAppearanceViewModel = hiltViewModel(),
    quickAccessViewModel: HomeQuickAccessViewModel = hiltViewModel(),
    onHomeAppearance: () -> Unit = {},
    onOrganization: () -> Unit = {},
    onHistory: () -> Unit = {},
    onMovies: () -> Unit = {},
    onSeries: () -> Unit = {},
    onProfiles: () -> Unit = {},
    profileName: String = "",
    onStartupReady: (String) -> Unit = {},
    onSearch: () -> Unit = {},
    openLiveRequested: Boolean = false,
    onOpenLiveHandled: () -> Unit = {}
) {
    HomeSessionLifecycle(viewModel)
    val activityTiming = remember { LoadTimings.activityTicket() }
    val parental = LocalParentalSession.current
    val liveLocked = !parental.state.allows(ParentalSection.LIVE)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteState.collectAsStateWithLifecycle()
    val quickAccess by quickAccessViewModel.uiState.collectAsStateWithLifecycle()
    val dashboardData = remember(state.channels, quickAccess) {
        val ids = if (quickAccess.sourceKey == state.browseScope) quickAccess.channelIds else emptyList()
        HomeDashboardSelection.project(state.channels, ids)
    }
    val savedAppearance by appearanceViewModel.appearance.collectAsStateWithLifecycle()
    val appearance = savedAppearance
    if (appearance == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val labels = remember(appearance.language) { HomeLabels(appearance.language) }
    LaunchedEffect(appearance.restoreBrowseSession, state.browseScope) {
        viewModel.configureHomeSession(appearance.restoreBrowseSession)
    }
    ReportDrawnWhen { !state.isLoading && (state.needsSource || state.channels.isNotEmpty() || state.errorMessage != null) }
    LaunchedEffect(state.isLoading, state.channels.isNotEmpty(), state.browseScope) {
        if (!state.isLoading && state.channels.isNotEmpty()) {
            withFrameNanos { }; withFrameNanos { }
            LoadTimings.homeDrawn(activityTiming)
            onStartupReady(state.browseScope)
            viewModel.onHomeReady()
        }
    }
    var dashboardVisible by rememberSaveable(state.browseScope, "cinematic-home-v3506") {
        mutableStateOf(true)
    }
    var launchedFromDashboard by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openLiveRequested) {
        if (openLiveRequested) {
            dashboardVisible = false
            viewModel.selectSection(false)
            onOpenLiveHandled()
        }
    }
    LaunchedEffect(state.browseScope, state.needsSource, dashboardVisible) {
        quickAccessViewModel.bind(state.browseScope.takeIf { dashboardVisible && !state.needsSource })
    }
    DisposableEffect(quickAccessViewModel) {
        onDispose { quickAccessViewModel.bind(null) }
    }
    val sidebarFocus = remember { HomeSection.entries.associateWith { FocusRequester() } }
    val dashboardFocus = remember { FocusRequester() }
    val liveContentFocus = remember { FocusRequester() }
    var lastSidebarFocus by rememberSaveable { mutableStateOf(HomeSection.DASHBOARD.name) }
    fun restoreSidebarFocus() {
        val target = HomeFocusPolicy.restore(lastSidebarFocus, state.isLoading || state.isRefreshing,
            !state.isLoading && !state.needsSource && state.errorMessage == null && state.channels.isNotEmpty())
        runCatching { sidebarFocus.getValue(HomeFocusPolicy.railTarget(target)).requestFocus() }
    }
    var initialFocusDone by remember(state.browseScope) { mutableStateOf(false) }
    LaunchedEffect(returnedChannelId, state.channels.isNotEmpty()) {
        if (state.channels.isNotEmpty()) returnedChannelId?.let {
            initialFocusDone = true
            dashboardVisible = launchedFromDashboard
            if (!launchedFromDashboard) viewModel.selectChannel(it)
            launchedFromDashboard = false
            onReturnHandled()
        }
    }
    LaunchedEffect(state.sessionReady, state.isLoading, state.browseScope) {
        if (state.sessionReady && !state.isLoading && !initialFocusDone) {
            initialFocusDone = true
            if (returnedChannelId != null) return@LaunchedEffect
            withFrameNanos { }; restoreSidebarFocus()
        }
    }
    BackHandler(enabled = !dashboardVisible) {
        if (state.query.isNotBlank()) viewModel.updateQuery("") else dashboardVisible = true
        restoreSidebarFocus()
    }
    val selected = when {
        dashboardVisible -> HomeSection.DASHBOARD
        state.favoritesOnly -> HomeSection.FAVORITES
        else -> HomeSection.LIVE
    }
    fun select(section: HomeSection) {
        initialFocusDone = true
        viewModel.onBrowseInteraction()
        when (section) {
            HomeSection.MORE -> Unit
            HomeSection.DASHBOARD -> { dashboardVisible = true }
            HomeSection.LIVE, HomeSection.FAVORITES -> {
                if (liveLocked) parental.model?.requestPin()
                else { dashboardVisible = false; viewModel.selectSection(section == HomeSection.FAVORITES) }
            }
            HomeSection.MOVIES -> onMovies()
            HomeSection.SERIES -> onSeries()
            HomeSection.SEARCH -> onSearch()
            HomeSection.HISTORY -> onHistory()
            HomeSection.GUIDE -> onOpenGuide(state.selectedChannelId, state.selectedGroup, state.query, state.favoritesOnly)
            HomeSection.SETTINGS -> onSettings()
            HomeSection.APPEARANCE -> onHomeAppearance()
            HomeSection.ORGANIZE -> onOrganization()
            HomeSection.REFRESH -> viewModel.refresh()
        }
    }
    val languageDirection = if (appearance.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides if (appearance.sidebarOnRight) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Row(Modifier.fillMaxSize().onPreviewKeyEvent {
            if (it.type == KeyEventType.KeyDown) {
                initialFocusDone = true
                viewModel.onBrowseInteraction()
            }
            false
        }) {
            CompositionLocalProvider(LocalLayoutDirection provides languageDirection) {
                HomeSidebar(appearance, labels, selected, state.isLoading || state.isRefreshing,
                    sidebarFocus, { section -> lastSidebarFocus = section.name }, ::select,
                    dashboardFocus = dashboardFocus,
                    browserFocus = liveContentFocus.takeIf { !dashboardVisible && !state.isLoading && !state.needsSource && state.channels.isNotEmpty() },
                    liveBrowser = !dashboardVisible, organizationReady = !state.isLoading && !state.needsSource && state.errorMessage == null && state.channels.isNotEmpty())
            }
            CompositionLocalProvider(LocalLayoutDirection provides languageDirection) {
                Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = if (dashboardVisible) 30.dp else 0.dp,
                    vertical = if (dashboardVisible) 20.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (!dashboardVisible) ParentalHomeControl()
                    if (state.isRefreshing && state.channels.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
                    FavoriteFeedback(favorites, viewModel::retryFavorites)
                    if (state.organization.readFailed) Text(stringResource(R.string.org_read_warning),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    when {
                        state.needsSource -> HomeMessage(labels.text("أدخل مصدر القنوات من الإعدادات.", "Add a source in settings.", "Quelle in Einstellungen hinzufügen."),
                            labels.section(HomeSection.SETTINGS), onSettings)
                        state.isLoading && state.visibleChannels.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        state.channels.isEmpty() && !liveLocked -> HomeMessage(labels.text("لا توجد قنوات محمّلة.", "No channels loaded.", "Keine Sender geladen."),
                            labels.section(HomeSection.REFRESH), viewModel::refresh)
                        dashboardVisible -> Box(Modifier.weight(1f).fillMaxWidth()) { key(state.browseScope) {
                            HomeDashboardScene(labels, appearance, profileName, dashboardData, state.channels,
                                state.browseScope, dashboardFocus, onProfiles, onSearch,
                                onPlay = { channel ->
                                    initialFocusDone = true
                                    launchedFromDashboard = true
                                    onPlayChannel(channel.id, null, "", false)
                                }, onLive = { select(HomeSection.LIVE) }, onMovies = onMovies, onSeries = onSeries,
                                onGuide = { onOpenGuide(null, null, "", false) }, onHistory = onHistory)
                        } }
                        else -> key(state.browseScope) {
                            LiveTvBrowserScene(state, appearance, labels, viewModel::updateQuery,
                                onGroup = { group ->
                                    initialFocusDone = true
                                    if (group == null) viewModel.selectAllGroups() else viewModel.selectGroup(group)
                                }, onPlay = { channel ->
                                    initialFocusDone = true
                                    launchedFromDashboard = false
                                    viewModel.selectChannel(channel.id)
                                    onPlayChannel(channel.id, state.selectedGroup, state.query, state.favoritesOnly)
                                }, onFavorite = { viewModel.toggleFavorite(it.id, state.browseScope) }, canFavorite = favorites.canToggle,
                                onPosition = { id, position ->
                                    initialFocusDone = true
                                    viewModel.onBrowsePosition(state.browseScope, state.selectedGroup,
                                        state.favoritesOnly, id, position)
                                }, onRestored = viewModel::onGridRestoreHandled,
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                sidebarFocus = sidebarFocus.getValue(selected), entryFocus = liveContentFocus)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeMessage(text: String, action: String, onAction: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(text, style = MaterialTheme.typography.titleMedium)
            AppButton(action, onAction)
        }
    }
}