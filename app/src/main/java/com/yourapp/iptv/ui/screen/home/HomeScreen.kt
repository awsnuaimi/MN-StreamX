package com.yourapp.iptv.ui.screen.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourapp.iptv.ui.component.channel.FavoriteFeedback
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.ui.tv.TvChannelGrid
import com.yourapp.iptv.viewmodel.HomeViewModel
import com.yourapp.iptv.viewmodel.HomeAppearanceViewModel

@Composable
fun HomeScreen(
    onSettings: () -> Unit,
    onPlayChannel: (String, String?, String, Boolean) -> Unit,
    onOpenGuide: (String?, String?, String, Boolean) -> Unit,
    returnedChannelId: String? = null,
    onReturnHandled: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    appearanceViewModel: HomeAppearanceViewModel = hiltViewModel(),
    onHomeAppearance: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteState.collectAsStateWithLifecycle()
    val savedAppearance by appearanceViewModel.appearance.collectAsStateWithLifecycle()
    val appearance = savedAppearance
    if (appearance == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val labels = remember(appearance.language) { HomeLabels(appearance.language) }
    var searching by rememberSaveable { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    val sidebarFocus = remember { FocusRequester() }
    LaunchedEffect(returnedChannelId) {
        returnedChannelId?.let { viewModel.selectChannel(it); onReturnHandled() }
    }
    LaunchedEffect(Unit) {
        if (returnedChannelId == null && state.selectedChannelId == null) {
            withFrameNanos { }; runCatching { sidebarFocus.requestFocus() }
        }
    }
    LaunchedEffect(searching) { if (searching && state.channels.isNotEmpty()) { withFrameNanos { }; runCatching { searchFocus.requestFocus() } } }
    BackHandler(enabled = searching) {
        searching = false; viewModel.updateQuery(""); runCatching { sidebarFocus.requestFocus() }
    }
    val selected = when { searching -> HomeSection.SEARCH; state.favoritesOnly -> HomeSection.FAVORITES; else -> HomeSection.LIVE }
    fun select(section: HomeSection) {
        when (section) {
            HomeSection.LIVE, HomeSection.FAVORITES -> { searching = false; viewModel.selectSection(section == HomeSection.FAVORITES) }
            HomeSection.SEARCH -> searching = true
            HomeSection.GUIDE -> onOpenGuide(state.selectedChannelId, state.selectedGroup, state.query, state.favoritesOnly)
            HomeSection.SETTINGS -> onSettings()
            HomeSection.APPEARANCE -> onHomeAppearance()
            HomeSection.REFRESH -> viewModel.refresh()
        }
    }
    val languageDirection = if (appearance.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    // Physical sidebar placement is independent of the direction of text and channel names.
    CompositionLocalProvider(LocalLayoutDirection provides if (appearance.sidebarOnRight) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            Spacer(Modifier.width(if (appearance.expandedSidebar) 180.dp else 70.dp))
            CompositionLocalProvider(LocalLayoutDirection provides languageDirection) {
                Column(Modifier.weight(1f).fillMaxHeight().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(labels.section(selected), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                            Text(labels.text("${state.visibleChannels.size} قناة ظاهرة من ${state.channels.size}",
                                "${state.visibleChannels.size} of ${state.channels.size} channels",
                                "${state.visibleChannels.size} von ${state.channels.size} Sendern"), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (searching || state.query.isNotBlank()) OutlinedTextField(state.query, viewModel::updateQuery,
                        label = { Text(labels.text("البحث عن قناة", "Search channels", "Sender suchen")) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth().focusRequester(searchFocus))
                    if (state.isLoading && state.channels.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
                    FavoriteFeedback(favorites, viewModel::retryFavorites)
                    state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    when {
                        state.needsSource -> HomeMessage(labels.text("أدخل مصدر القنوات من الإعدادات.", "Add a source in settings.", "Quelle in Einstellungen hinzufügen."),
                            labels.section(HomeSection.SETTINGS), onSettings)
                        state.isLoading && state.channels.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        state.channels.isEmpty() -> HomeMessage(labels.text("لا توجد قنوات محمّلة.", "No channels loaded.", "Keine Sender geladen."),
                            labels.section(HomeSection.REFRESH), viewModel::refresh)
                        else -> Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            HomeCategories(state.groups, state.selectedGroup, labels,
                                Modifier.widthIn(min = 140.dp, max = 190.dp).fillMaxWidth(0.24f).fillMaxHeight()) { group ->
                                // Selecting all categories must preserve the Favorites section.
                                if (group == null) viewModel.selectAllGroups() else viewModel.selectGroup(group)
                            }
                            if (state.visibleChannels.isEmpty()) Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(labels.text("لا توجد قنوات تطابق الاختيار.", "No matching channels.", "Keine passenden Sender."))
                                    AppButton(labels.text("مسح التصفية", "Clear filters", "Filter löschen"), viewModel::clearFilters)
                                }
                            } else TvChannelGrid(state.visibleChannels, state.selectedChannelId, { channel ->
                                viewModel.selectChannel(channel.id)
                                onPlayChannel(channel.id, state.selectedGroup, state.query, state.favoritesOnly)
                            }, Modifier.weight(1f).fillMaxHeight(), columns = appearance.columns, showGroup = state.selectedGroup == null)
                        }
                    }
                }
            }
        }
        CompositionLocalProvider(LocalLayoutDirection provides languageDirection) {
            HomeSidebar(appearance, labels, selected, state.isLoading, sidebarFocus, ::select,
                Modifier.align(if (appearance.sidebarOnRight) AbsoluteAlignment.CenterRight else AbsoluteAlignment.CenterLeft).zIndex(1f))
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