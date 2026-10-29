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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.ui.presentation.*
import com.yourapp.iptv.ui.state.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** A pure UI boundary. No player, audio capture, provider request or durable setting is started here. */
@Composable
internal fun LiveTvBrowser(state: HomeUiState, appearance: HomeAppearance, labels: HomeLabels,
    guides: HomeGuideState, accessEpoch: Long, captions: CaptionUiState,
    onQuery: (String) -> Unit, onGroup: (String?) -> Unit, onPlay: (Channel) -> Unit,
    onTranslate: (Channel) -> Unit, onFavorite: (Channel) -> Unit, canFavorite: Boolean,
    onPosition: (String, HomeGridPosition) -> Unit, onRestored: (Long) -> Unit,
    onGuideChannels: (List<Channel>) -> Unit, modifier: Modifier = Modifier,
    projectionDispatcher: CoroutineDispatcher = Dispatchers.Default,
    sidebarFocus: FocusRequester? = null, entryFocus: FocusRequester? = null) {
    val colors = liveTvColors(appearance)
    var sort by rememberSaveable(state.browseScope) { mutableStateOf(LiveChannelSort.PROVIDER) }
    val input = remember(state.visibleChannels, sort, appearance.language, projectionDispatcher) { Any() }
    val projected by produceState<Pair<Any, List<LiveChannelRow>>?>(null, input) {
        value = input to withContext(projectionDispatcher) {
            LiveChannelRows.project(state.visibleChannels, sort, Locale.forLanguageTag(when (appearance.language) {
                HomeLanguage.ARABIC -> "ar"; HomeLanguage.ENGLISH -> "en"; HomeLanguage.GERMAN -> "de"
            }))
        }
    }
    val rows = projected?.takeIf { it.first === input }?.second.orEmpty()
    var focused by remember(state.browseScope, state.selectedGroup, state.query, state.favoritesOnly) {
        mutableStateOf<String?>(null)
    }
    val active = rows.firstOrNull { row -> row.variants.any { it.id == focused } }
        ?: rows.firstOrNull { row -> row.variants.any { it.id == state.selectedChannelId } } ?: rows.firstOrNull()
    val channel = active?.channel(focused ?: state.selectedChannelId)
    var menuKey by remember(state.browseScope, state.selectedGroup, state.query, accessEpoch) { mutableStateOf<String?>(null) }
    val menu = rows.firstOrNull { it.key == menuKey }
    val categories = remember { FocusRequester() }; val watch = remember { FocusRequester() }
    val defaultEntry = remember { FocusRequester() }
    val entry = entryFocus ?: defaultEntry
    val headerFocus = remember { FocusRequester() }
    val navigation = remember { LiveTvListNavigation() }
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
        LiveTvHeader(labels.section(if (state.favoritesOnly) HomeSection.FAVORITES else HomeSection.LIVE),
            state.visibleChannels.size, state.channels.size, state.query, onQuery, sort, { sort = it }, labels, colors, categories, headerFocus)
        LiveTvCategories(state.groups, state.channels, state.organization, state.selectedGroup, labels, colors, categories, onGroup, projectionDispatcher, entry, headerFocus)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val detail = maxWidth >= 680.dp && LocalDensity.current.fontScale <= 1.6f
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Box(Modifier.weight(if (detail) 2.04f else 1f).fillMaxHeight()
                    .onPreviewKeyEvent(navigation::handle).focusRequester(entry)
                    .onFocusChanged {
                        navigation.ownsFocus = it.hasFocus
                        if (it.isFocused) navigation.enter() else if (!it.hasFocus) navigation.cancel()
                    }.focusable().testTag("live-list-entry")) {
                    LiveTvChannelList(rows, state.selectedChannelId, active?.key, state.gridRestore, labels, colors,
                        program, { HomeChannelPresentation.badge(it, captions) }, { focused = it.id }, onPlay,
                        { focused = it.channel(focused).id; menuKey = it.key }, { visible = it }, onPosition, onRestored,
                        categories, watch, detail, Modifier.fillMaxSize(), appearance.showChannelLogos,
                        navigation, sidebarFocus, ready = projected?.first === input)
                    if (projected?.first !== input) Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
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
        }
        Text(labels.text("↑↓ قنوات · CH± صفحات · OK مشاهدة · مطوّل خيارات", "↑↓ Channels · CH± Pages · OK Watch · Hold OK Options",
            "↑↓ Sender · CH± Seiten · OK ansehen · OK halten: Optionen"), color = colors.muted, fontSize = 10.sp,
            modifier = Modifier.testTag("live-browse-hint"))
    }
    menu?.let { row ->
        LiveTvChannelMenu(row, row.channel(focused), labels, colors, canFavorite, onDismiss = { menuKey = null; runCatching { entry.requestFocus() } },
            onFavorite = { onFavorite(row.channel(focused)); menuKey = null; runCatching { entry.requestFocus() } },
            onPlay = { menuKey = null; onPlay(it) },
            onTranslate = { menuKey = null; onTranslate(row.channel(focused)) })
    }
}