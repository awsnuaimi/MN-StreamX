package com.yourapp.iptv.ui.screen.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.performance.*
import com.yourapp.iptv.domain.organization.FolderFilter
import com.yourapp.iptv.ui.presentation.*
import com.yourapp.iptv.ui.state.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** A pure UI boundary. No player, audio capture, provider request or durable setting is started here. */
@Composable
internal fun LiveTvBrowser(catalog: HomeUiState, appearance: HomeAppearance, labels: HomeLabels,
    guides: HomeGuideState, accessEpoch: Long, captions: CaptionUiState,
    onQuery: (String) -> Unit, onGroup: (String?) -> Unit, onPlay: (Channel) -> Unit,
    onTranslate: (Channel) -> Unit, onFavorite: (Channel) -> Unit, canFavorite: Boolean,
    onPosition: (String, HomeGridPosition) -> Unit, onRestored: (Long) -> Unit,
    onGuideChannels: (List<Channel>) -> Unit, modifier: Modifier = Modifier,
    projectionDispatcher: CoroutineDispatcher = Dispatchers.Default,
    sidebarFocus: FocusRequester? = null, entryFocus: FocusRequester? = null,
    preferences: LiveTvPreferencesState = LiveTvPreferencesState(ready = true),
    onPreference: (((LiveTvPreferences) -> LiveTvPreferences) -> Unit)? = null,
    onPreferencesRetry: () -> Unit = {}) {
    val colors = liveTvColors(appearance)
    LaunchedEffect(catalog.browseScope, catalog.selectedGroup, preferences.value.hiddenGroups, preferences.value.hiddenFolders) {
        val selected = catalog.selectedGroup
        val folder = FolderFilter.id(selected)
        if (selected in preferences.value.hiddenGroups || (folder != null &&
            catalog.organization.path(folder).any { it.id in preferences.value.hiddenFolders })) onGroup(null)
    }

    var localSort by rememberSaveable(catalog.browseScope) { mutableStateOf(LiveChannelSort.PROVIDER) }
    val sort = if (onPreference == null) localSort else LiveChannelSort.valueOf(preferences.value.sort.name)
    val input = remember(catalog.browseScope, catalog.visibleChannels, catalog.channels, catalog.groups, catalog.organization, sort,
        preferences.value.hiddenChannels, preferences.value.hiddenGroups, preferences.value.hiddenFolders,
        preferences.ready, appearance.language, accessEpoch, projectionDispatcher) { Any() }
    val numbering = remember(catalog.browseScope) { LiveChannelNumbers() }
    val projected by produceState<Pair<Any, Pair<HomeUiState, LiveChannelIndex>>?>(null, input) {
        if (preferences.ready) value = input to PerformanceJournal.measure(PerformanceStage.LIVE_PROJECT) {
            withContext(projectionDispatcher) {
            val visible = LiveTvPersonalization.project(catalog, preferences.value)
            val numbers = numbering.forCatalog(catalog.channels)
            visible to LiveChannelIndex(numbering.apply(LiveChannelRows.project(visible.visibleChannels, sort, Locale.forLanguageTag(when (appearance.language) {
                HomeLanguage.ARABIC -> "ar"; HomeLanguage.ENGLISH -> "en"; HomeLanguage.GERMAN -> "de"
            })), numbers))
        } }
    }
    val personal = projected?.takeIf { it.first === input }?.second?.first
    val state = if (personal == null) catalog.copy(visibleChannels = emptyList()) else catalog.copy(
        channels = personal.channels, visibleChannels = personal.visibleChannels, groups = personal.groups, organization = personal.organization)
    val rowIndex = projected?.takeIf { it.first === input }?.second?.second ?: LiveChannelIndex.Empty
    val rows = rowIndex.rows
    var manage by remember(catalog.browseScope, accessEpoch) { mutableStateOf(false) }
    var lastIndex by remember(catalog.browseScope, catalog.selectedGroup, catalog.query, catalog.favoritesOnly) { mutableIntStateOf(0) }
    var focused by remember(state.browseScope, state.selectedGroup, state.query, state.favoritesOnly) {
        mutableStateOf<String?>(null)
    }
    val active = rowIndex.forChannel(focused)
        ?: rowIndex.forChannel(state.selectedChannelId) ?: rows.getOrNull(lastIndex.coerceAtMost(rows.lastIndex).coerceAtLeast(0))
    val channel = active?.channel(focused ?: state.selectedChannelId, preferences.value.preferredQualities)
    var menuKey by remember(state.browseScope, state.selectedGroup, state.query, accessEpoch) { mutableStateOf<String?>(null) }
    val menu = rowIndex.forKey(menuKey)
    val categories = remember { FocusRequester() }; val watch = remember { FocusRequester() }
    val defaultEntry = remember { FocusRequester() }
    val entry = entryFocus ?: defaultEntry
    val headerFocus = remember { FocusRequester() }
    val navigation = remember { LiveTvListNavigation() }
    val numeric = remember(input, catalog.selectedGroup, catalog.query, catalog.favoritesOnly, accessEpoch) { LiveTvNumberEntry() }
    val numberRows = rowIndex.numberedRows
    val numberKeys = liveTvNumberKeys(numeric, numberRows, projected?.first === input,
        LocalViewConfiguration.current.longPressTimeoutMillis,
        jump = { row -> entry.requestFocus(); navigation.request(row.key, instant = true) },
        play = { row -> navigation.cancel(); onPlay(row.channel(focused, preferences.value.preferredQualities)) })
    LiveTvNumberLifetime(numeric, navigation::cancel)
    LaunchedEffect(menuKey, manage) { if (menuKey != null || manage) numeric.clear() }
    var detailsFocused by remember { mutableStateOf(false) }
    var observedInput by remember { mutableStateOf(input) }
    val returnToList = observedInput !== input && navigation.ownsFocus
    SideEffect {
        observedInput = input
        if (returnToList) { navigation.enter(); entry.requestFocus() }
    }
    BackHandler(detailsFocused && rows.isNotEmpty()) { entry.requestFocus() }
    val program: (Channel) -> ChannelProgramInfo = { ChannelProgramInfo.from(it, guides.forChannel(it, state.browseScope, accessEpoch)) }
    val translated = HomeChannelPresentation.kind(captions) == HomeTranslationKind.GENERATED
    val request by rememberUpdatedState(onGuideChannels)
    var visible by remember(state.browseScope, state.selectedGroup, state.query) { mutableStateOf<List<Channel>>(emptyList()) }
    LaunchedEffect(visible, channel) { request((visible + listOfNotNull(channel)).distinctBy { it.id to it.streamUrl }.take(40)) }
    DisposableEffect(Unit) { onDispose { request(emptyList()) } }
    Column(modifier.fillMaxSize().background(Brush.linearGradient(listOf(
        if (appearance.theme == HomeTheme.DARK_BLUE && !appearance.isNightMode) Color(0xFF112B56) else colors.background,
        colors.background, colors.background))).padding(start = 30.dp, end = 30.dp, top = 16.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        LiveTvPreferenceStatus(preferences, labels, onPreferencesRetry)
        LiveTvHeader(labels.section(if (state.favoritesOnly) HomeSection.FAVORITES else HomeSection.LIVE),
            state.visibleChannels.size, state.channels.size, state.query, onQuery, sort, { value -> if (onPreference == null) localSort = value else onPreference { it.copy(sort = LiveTvSort.valueOf(value.name)) } }, labels, colors, categories, headerFocus,
            onManage = onPreference?.takeIf { preferences.ready }?.let { { manage = true } }, canSort = preferences.ready && !preferences.saving)
        LiveTvCategories(state.groups, state.channels, state.organization, state.selectedGroup, labels, colors, categories, onGroup, projectionDispatcher, entry, headerFocus)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().onPreviewKeyEvent(numberKeys)
            .onFocusChanged { if (!it.hasFocus) numeric.clear() }.focusGroup()) {
            val detail = maxWidth >= 680.dp && LocalDensity.current.fontScale <= 1.6f
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Box(Modifier.weight(if (detail) 2.04f else 1f).fillMaxHeight()
                    .onPreviewKeyEvent(navigation::handle).focusRequester(entry)
                    .onFocusChanged {
                        navigation.ownsFocus = it.hasFocus
                        if (it.isFocused) navigation.enter() else if (!it.hasFocus) navigation.cancel()
                    }.focusable().testTag("live-list-entry")) {
                    LiveTvChannelList(rows, state.selectedChannelId, active?.key, state.gridRestore, labels, colors,
                        program, { HomeChannelPresentation.badge(it, captions) }, { focused = it.id; lastIndex = rowIndex.channelPosition(it.id).coerceAtLeast(0) }, onPlay,
                        { focused = it.channel(focused, preferences.value.preferredQualities).id; menuKey = it.key }, { visible = it }, onPosition, onRestored,
                        categories, watch, detail, Modifier.fillMaxSize(), appearance.showChannelLogos,
                        navigation, sidebarFocus, ready = projected?.first === input, preferredQualities = preferences.value.preferredQualities, index = rowIndex)
                    if (!preferences.ready && preferences.error) Column(Modifier.align(Alignment.Center)) {
                        Text(labels.text("تعذّر تحميل تفضيلات القائمة.", "Cannot load list preferences.", "Listeneinstellungen konnten nicht geladen werden."))
                        TextButton(onClick = onPreferencesRetry) { Text(labels.text("إعادة المحاولة", "Retry", "Erneut versuchen")) }
                    } else if (projected?.first !== input) Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    } else if (rows.isEmpty()) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(labels.text("لا توجد قنوات تطابق الاختيار.", "No matching channels.", "Keine passenden Sender."), color = colors.text)
                        LiveTvPill(labels.text("مسح التصفية", "Clear filters", "Filter löschen"), colors,
                            { onQuery(""); onGroup(null) })
                    }
                }
                if (detail && active != null && channel != null) LiveTvDetail(active, channel, program(channel),
                    guides.forChannel(channel, state.browseScope, accessEpoch).nowMillis, colors, labels, translated,
                    captions.settings.target.tag.uppercase(Locale.ROOT), watch, entry, { onPlay(channel) },
                    { onTranslate(channel) }, Modifier.weight(1f).fillMaxHeight().onFocusChanged { detailsFocused = it.hasFocus }.focusGroup())
            }
            if (numeric.active) LiveTvNumberOverlay(numeric, numberRows[numeric.number], labels, colors,
                Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
        Text(labels.text("0–9 انتقال · CH± صفحات · OK مشاهدة / مطوّل خيارات", "0–9 Number · CH± Pages · OK Watch / Hold Options",
            "0–9 Sendernummer · CH± Seiten · OK ansehen / halten: Optionen"), color = colors.muted, fontSize = 10.sp,
            modifier = Modifier.testTag("live-browse-hint"))
    }
    BackHandler(numeric.active) { numeric.clear(); navigation.cancel() }
    menu?.let { row ->
        LiveTvChannelMenu(row, row.channel(focused, preferences.value.preferredQualities), labels, colors, canFavorite, onDismiss = { menuKey = null; runCatching { entry.requestFocus() } },
            onFavorite = { onFavorite(row.channel(focused, preferences.value.preferredQualities)); menuKey = null; runCatching { entry.requestFocus() } },
            onPlay = { menuKey = null; onPlay(it) },
            onTranslate = { menuKey = null; onTranslate(row.channel(focused, preferences.value.preferredQualities)) },
            onHide = onPreference?.let { change -> { change { it.copy(hiddenChannels = it.hiddenChannels + row.variants.map { c -> c.id }) }; menuKey = null; entry.requestFocus() } },
            onHideCategory = onPreference?.takeIf { state.selectedGroup != null || row.variants.first().group != null }?.let { change -> {
                val folder = FolderFilter.id(state.selectedGroup)
                val group = state.selectedGroup ?: row.variants.first().group
                change { if (folder != null) it.copy(hiddenFolders = it.hiddenFolders + folder)
                    else it.copy(hiddenGroups = it.hiddenGroups + listOfNotNull(group)) }
                menuKey = null; onGroup(null); entry.requestFocus()
            } },
            onPreferred = onPreference?.let { change -> { selected ->
                change { it.prefer(row.preferenceKey, selected?.id) }
                menuKey = null; entry.requestFocus()
            } }, preferredQualities = preferences.value.preferredQualities, saving = preferences.saving)
    }
    if (manage && onPreference != null) LiveTvHiddenDialog(catalog, preferences.value, labels, colors,
        preferences.saving, onPreference) { manage = false; headerFocus.requestFocus() }
}