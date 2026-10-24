package com.yourapp.iptv.ui.screen.home

import androidx.compose.ui.res.stringResource
import com.yourapp.iptv.R
import com.yourapp.iptv.ui.component.organization.FolderBrowser
import androidx.activity.compose.BackHandler
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourapp.iptv.ui.component.channel.FavoriteFeedback
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.ui.tv.TvChannelGrid
import com.yourapp.iptv.viewmodel.HomeViewModel
import com.yourapp.iptv.viewmodel.HomeAppearanceViewModel
import com.yourapp.iptv.viewmodel.HomeQuickAccessViewModel
import com.yourapp.iptv.domain.model.HomeDashboardSelection

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
    onStartupReady: (String) -> Unit = {}
) {
    HomeSessionLifecycle(viewModel)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteState.collectAsStateWithLifecycle()
    val quickAccess by quickAccessViewModel.uiState.collectAsStateWithLifecycle()
    val dashboardData = remember(state.channels, quickAccess) {
        val ids = if (quickAccess.sourceKey == state.browseScope) quickAccess.channelIds else emptyList()
        HomeDashboardSelection.project(state.channels, ids)
    }
    val savedAppearance by appearanceViewModel.appearance.collectAsStateWithLifecycle()
    val themeSaving by appearanceViewModel.themeSaving.collectAsStateWithLifecycle()
    val themeError by appearanceViewModel.themeError.collectAsStateWithLifecycle()
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
            onStartupReady(state.browseScope)
            viewModel.onHomeReady()
        }
    }
    var searching by rememberSaveable { mutableStateOf(false) }
    var dashboardVisible by rememberSaveable(state.browseScope) { mutableStateOf(true) }
    // History is observed only while Smart Home is visible. Leaving it releases the subscription.
    LaunchedEffect(state.browseScope, state.needsSource, dashboardVisible) {
        quickAccessViewModel.bind(state.browseScope.takeIf { dashboardVisible && !state.needsSource })
    }
    DisposableEffect(quickAccessViewModel) {
        onDispose { quickAccessViewModel.bind(null) }
    }
    val searchFocus = remember { FocusRequester() }
    val sidebarFocus = remember { HomeSection.entries.associateWith { FocusRequester() } }
    val dashboardFocus = remember { FocusRequester() }
    var lastSidebarFocus by rememberSaveable { mutableStateOf(HomeSection.DASHBOARD.name) }
    fun restoreSidebarFocus() {
        val target = HomeFocusPolicy.restore(lastSidebarFocus, state.isLoading || state.isRefreshing,
            !state.isLoading && !state.needsSource && state.errorMessage == null && state.channels.isNotEmpty())
        runCatching { sidebarFocus.getValue(target).requestFocus() }
    }
    var initialFocusDone by remember(state.browseScope) { mutableStateOf(false) }
    LaunchedEffect(returnedChannelId, state.visibleChannels.isNotEmpty()) {
        if (state.visibleChannels.isNotEmpty()) returnedChannelId?.let {
            initialFocusDone = true; dashboardVisible = false; viewModel.selectChannel(it); onReturnHandled()
        }
    }
    LaunchedEffect(state.sessionReady, state.isLoading, state.browseScope) {
        if (state.sessionReady && !state.isLoading && !initialFocusDone) {
            initialFocusDone = true
            if (returnedChannelId != null) return@LaunchedEffect
            withFrameNanos { }; restoreSidebarFocus()
        }
    }
    LaunchedEffect(searching) { if (searching && state.channels.isNotEmpty()) { withFrameNanos { }; runCatching { searchFocus.requestFocus() } } }
    BackHandler(enabled = searching) {
        searching = false; viewModel.updateQuery(""); restoreSidebarFocus()
    }
    BackHandler(enabled = !searching && !dashboardVisible) {
        dashboardVisible = true
        restoreSidebarFocus()
    }
    val selected = when {
        searching -> HomeSection.SEARCH
        dashboardVisible -> HomeSection.DASHBOARD
        state.favoritesOnly -> HomeSection.FAVORITES
        else -> HomeSection.LIVE
    }
    fun select(section: HomeSection) {
        initialFocusDone = true
        viewModel.onBrowseInteraction()
        when (section) {
            HomeSection.DASHBOARD -> { searching = false; dashboardVisible = true }
            HomeSection.LIVE, HomeSection.FAVORITES -> { searching = false; dashboardVisible = false; viewModel.selectSection(section == HomeSection.FAVORITES) }
            HomeSection.MOVIES -> onMovies()
            HomeSection.SERIES -> onSeries()
            HomeSection.SEARCH -> { dashboardVisible = false; searching = true }
            HomeSection.HISTORY -> onHistory()
            HomeSection.GUIDE -> onOpenGuide(state.selectedChannelId, state.selectedGroup, state.query, state.favoritesOnly)
            HomeSection.SETTINGS -> onSettings()
            HomeSection.APPEARANCE -> onHomeAppearance()
            HomeSection.ORGANIZE -> onOrganization()
            HomeSection.REFRESH -> viewModel.refresh()
        }
    }
    val languageDirection = if (appearance.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    // Physical sidebar placement is independent of the direction of text and channel names.
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
                    onToggleTheme = appearanceViewModel::toggleTheme,
                    themeSaving = themeSaving, themeError = themeError,
                    dashboardFocus = dashboardFocus,
                    organizationReady = !state.isLoading && !state.needsSource && state.errorMessage == null && state.channels.isNotEmpty())
            }
            CompositionLocalProvider(LocalLayoutDirection provides languageDirection) {
                Column(Modifier.weight(1f).fillMaxHeight().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!dashboardVisible) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(labels.section(selected), style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary)
                            Text(labels.text("${state.visibleChannels.size} قناة ظاهرة من ${state.channels.size}",
                                "${state.visibleChannels.size} of ${state.channels.size} channels",
                                "${state.visibleChannels.size} von ${state.channels.size} Sendern"),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (!dashboardVisible && (searching || state.query.isNotBlank())) OutlinedTextField(state.query, viewModel::updateQuery,
                        label = { Text(labels.text("البحث عن قناة", "Search channels", "Sender suchen")) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth().focusRequester(searchFocus))
                    if (state.isRefreshing && state.channels.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
                    FavoriteFeedback(favorites, viewModel::retryFavorites)
                    if (state.organization.readFailed) Text(stringResource(R.string.org_read_warning),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    when {
                        state.needsSource -> HomeMessage(labels.text("أدخل مصدر القنوات من الإعدادات.", "Add a source in settings.", "Quelle in Einstellungen hinzufügen."),
                            labels.section(HomeSection.SETTINGS), onSettings)
                        state.isLoading && state.visibleChannels.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        state.channels.isEmpty() -> HomeMessage(labels.text("لا توجد قنوات محمّلة.", "No channels loaded.", "Keine Sender geladen."),
                            labels.section(HomeSection.REFRESH), viewModel::refresh)
                        dashboardVisible -> Box(Modifier.weight(1f).fillMaxWidth()) { HomeDashboard(labels, dashboardData, state.channels.size,
                            dashboardFocus = dashboardFocus,
                            isDarkTheme = appearance.isDarkTheme,
                            onPlayChannel = { channel ->
                                initialFocusDone = true
                                onPlayChannel(channel.id, null, "", false)
                            },
                            onLive = { select(HomeSection.LIVE) },
                            onMovies = onMovies,
                            onSeries = onSeries,
                            onGuide = { onOpenGuide(null, null, "", false) },
                            onHistory = onHistory) }
                        else -> BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        val categoryWidth = (maxWidth * 0.28f).coerceIn(160.dp, 220.dp)
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            val categoryModifier = Modifier.width(categoryWidth).fillMaxHeight()
                            val chooseGroup: (String?) -> Unit = { group ->
                                initialFocusDone = true
                                if (group == null) viewModel.selectAllGroups() else viewModel.selectGroup(group)
                            }
                            if (state.organization.enabled) FolderBrowser(state.organization, state.selectedGroup,
                                categoryModifier, restoreFocusOnNavigation = state.gridRestore == null, onSelect = chooseGroup)
                            else HomeCategories(state.groups, state.selectedGroup, labels, categoryModifier, chooseGroup)
                            if (state.visibleChannels.isEmpty()) Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(labels.text("لا توجد قنوات تطابق الاختيار.", "No matching channels.", "Keine passenden Sender."))
                                    AppButton(labels.text("مسح التصفية", "Clear filters", "Filter löschen"), viewModel::clearFilters)
                                }
                            } else key(state.browseScope, state.selectedGroup, state.favoritesOnly, state.query) {
                                TvChannelGrid(state.visibleChannels, state.selectedChannelId, { channel ->
                                    initialFocusDone = true
                                    viewModel.selectChannel(channel.id)
                                    onPlayChannel(channel.id, state.selectedGroup, state.query, state.favoritesOnly)
                                }, Modifier.weight(1f).fillMaxHeight(), columns = appearance.columns,
                                    showGroup = state.selectedGroup == null, restore = state.gridRestore,
                                    onBrowsePosition = { id, position ->
                                        initialFocusDone = true
                                        viewModel.onBrowsePosition(state.browseScope, state.selectedGroup,
                                            state.favoritesOnly, id, position)
                                    }, onRestoreHandled = viewModel::onGridRestoreHandled)
                            }
                        }
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