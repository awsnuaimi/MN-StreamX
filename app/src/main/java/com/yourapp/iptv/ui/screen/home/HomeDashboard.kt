package com.yourapp.iptv.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.yourapp.iptv.ui.component.common.rememberedFocusGroup
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.ui.state.HomeProgramUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import com.yourapp.iptv.ui.state.CaptionUiState
import com.yourapp.iptv.ui.state.HomeGuideState
import com.yourapp.iptv.ui.presentation.HomeChannelPresentation

/** Hero first, then actual channels and recent viewing. No player is created here. */
@Composable
internal fun HomeDashboard(labels: HomeLabels, selection: HomeDashboardSelection, channelCount: Int,
    dashboardFocus: FocusRequester, suggestedChannels: List<Channel>, isDarkTheme: Boolean, isNightMode: Boolean,
    onPlayChannel: (Channel) -> Unit, onLive: () -> Unit, onMovies: () -> Unit, onSeries: () -> Unit,
    onGuide: () -> Unit, onHistory: () -> Unit, programState: HomeProgramUiState = HomeProgramUiState(),
    onPreviewChannel: (Channel?) -> Unit = {},
    theme: HomeTheme = if (isDarkTheme) HomeTheme.DARK_BLUE else HomeTheme.LIGHT,
    showChannelLogos: Boolean = true, catalogChannels: List<Channel> = suggestedChannels,
    captionState: CaptionUiState = CaptionUiState(), interfaceLanguage: HomeLanguage = HomeLanguage.ARABIC,
    guideState: HomeGuideState = HomeGuideState(), guideOwner: String = "", accessEpoch: Long = -1,
    onGuideChannels: (List<Channel>) -> Unit = {}, headerFocus: FocusRequester? = null,
    sidebarFocus: FocusRequester? = null, sidebarOnRight: Boolean = false, heroEntry: FocusRequester? = null) {
    val colors = HomeTvPalettes.forTheme(theme, isNightMode)
    val languageRow = remember(selection, catalogChannels, captionState, interfaceLanguage) {
        HomeChannelPresentation.select(catalogChannels, selection, captionState, interfaceLanguage)
    }
    val primary = languageRow.channels
    val defaultHero = remember { FocusRequester() }
    val heroFocus = heroEntry ?: defaultHero
    val primaryFocus = remember { FocusRequester() }
    val primaryFirst = remember { FocusRequester() }
    val recentFocus = remember { FocusRequester() }
    val belowHero = if (primary.isNotEmpty()) primaryFocus else recentFocus.takeIf { selection.recent.isNotEmpty() }
    var viewport by remember { mutableStateOf(Rect.Zero) }
    val visibleRows = remember { mutableStateMapOf<String, List<Channel>>() }
    val bindGuides by rememberUpdatedState(onGuideChannels)
    val candidates = remember(selection, primary) { (selection.recent + primary).distinctBy { it.id to it.streamUrl } }
    var focusedChannel by remember { mutableStateOf<Channel?>(null) }
    var previewKey by remember { mutableStateOf<Pair<String, String>?>(null) }
    val highlighted = candidates.firstOrNull { (it.id to it.streamUrl) == previewKey }
        ?: selection.highlighted ?: primary.firstOrNull()
    LaunchedEffect(focusedChannel) {
        val channel = focusedChannel ?: return@LaunchedEffect
        delay(280); previewKey = channel.id to channel.streamUrl
    }
    LaunchedEffect(highlighted) { onPreviewChannel(highlighted) }
    val requested = visibleRows.values.flatten().distinctBy { it.id to it.streamUrl }
        .filterNot { it.id == highlighted?.id && it.streamUrl == highlighted.streamUrl }.take(8)
    LaunchedEffect(requested) { bindGuides(requested) }
    DisposableEffect(Unit) { onDispose { bindGuides(emptyList()) } }
    val cardGuide: (Channel) -> HomeProgramUiState = { channel ->
        if (programState.matches(channel)) programState else guideState.forChannel(channel, guideOwner, accessEpoch)
    }
    val badge: (Channel) -> String? = { HomeChannelPresentation.badge(it, captionState) }
    val onFocus: (Channel, Boolean) -> Unit = { channel, focused ->
        if (focused) focusedChannel = channel
        else if (focusedChannel?.id == channel.id && focusedChannel?.streamUrl == channel.streamUrl) focusedChannel = null
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background).onGloballyPositioned { viewport = it.boundsInRoot() }) {
        val metrics = remember(maxWidth) { HomeDashboardLayout.forWidth(maxWidth.value.toInt()) }
        Column(Modifier.fillMaxSize().testTag("home-dashboard-scroll").rememberedFocusGroup(dashboardFocus,
            if (highlighted != null) heroFocus else headerFocus, scrollable = true).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            HomeHero(highlighted, labels, colors, programState, showChannelLogos, onPlayChannel, focus = heroFocus,
                modifier = Modifier.homeFocusLinks(up = headerFocus, down = belowHero,
                    left = sidebarFocus.takeUnless { sidebarOnRight }, right = sidebarFocus.takeIf { sidebarOnRight }),
                languageBadge = highlighted?.let(badge), qualityBadge = highlighted?.let(HomeChannelPresentation::quality))
            if (primary.isNotEmpty()) DashboardChannelStrip(if (languageRow.languageSpecific)
                labels.text("بلغتك", "In your language", "In deiner Sprache") else labels.text("من قنواتك", "Your channels", "Deine Sender"),
                primary, metrics, colors, labels, onFocus, onPlayChannel, showChannelLogos,
                viewport = viewport, onVisible = { visibleRows["primary"] = it }, program = cardGuide, badge = badge,
                firstFocus = primaryFirst, focusModifier = { firstMounted ->
                    Modifier.rememberedFocusGroup(primaryFocus, primaryFirst.takeIf { firstMounted }, scrollable = true) { direction ->
                    homeRegionExit(direction, above = heroFocus, below = recentFocus.takeIf { selection.recent.isNotEmpty() },
                        sidebar = sidebarFocus, sidebarOnRight = sidebarOnRight, spatialBelow = true)
                } })
            if (selection.recent.isNotEmpty()) DashboardChannelStrip(labels.text("شوهد مؤخرًا", "Recently watched", "Zuletzt gesehen"),
                selection.recent, metrics, colors, labels, onFocus, onPlayChannel, showChannelLogos, tagPrefix = "recent-channel",
                viewport = viewport, onVisible = { visibleRows["recent"] = it }, program = cardGuide, badge = badge,
                focusModifier = { Modifier.rememberedFocusGroup(recentFocus, scrollable = true) { direction ->
                    homeRegionExit(direction, above = if (primary.isNotEmpty()) primaryFocus else heroFocus,
                        sidebar = sidebarFocus, sidebarOnRight = sidebarOnRight, rememberAbove = primary.isNotEmpty())
                } })
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Kept for callers and rendering tests; the Hero owns its actual layout. */
@Composable
internal fun DashboardWelcome(labels: HomeLabels, highlighted: Channel?, channelCount: Int,
    metrics: HomeDashboardMetrics, colors: HomeTvPalette, programState: HomeProgramUiState = HomeProgramUiState(),
    showChannelLogos: Boolean = true, onPlayChannel: (Channel) -> Unit) =
    HomeHero(highlighted, labels, colors, programState, showChannelLogos, onPlayChannel)

@Composable
private fun DashboardChannelStrip(title: String, channels: List<Channel>, metrics: HomeDashboardMetrics,
    colors: HomeTvPalette, labels: HomeLabels, onFocus: (Channel, Boolean) -> Unit, onPlay: (Channel) -> Unit,
    showLogos: Boolean, tagPrefix: String = "home-channel", viewport: Rect = Rect.Zero,
    onVisible: (List<Channel>) -> Unit = {}, program: (Channel) -> HomeProgramUiState = { HomeProgramUiState() },
    badge: (Channel) -> String? = { null }, firstFocus: FocusRequester? = null,
    focusModifier: (Boolean) -> Modifier = { Modifier }) {
    val list = rememberLazyListState()
    val firstMounted by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.any { it.index == 0 } } }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val report by rememberUpdatedState(onVisible)
    val minimumVisible = with(LocalDensity.current) { 24.dp.toPx() }
    LaunchedEffect(list, channels, viewport, minimumVisible) {
        snapshotFlow {
            if (!bounds.overlaps(viewport) || minOf(bounds.bottom, viewport.bottom) - maxOf(bounds.top, viewport.top) < minimumVisible) emptyList()
            else list.layoutInfo.visibleItemsInfo.mapNotNull { channels.getOrNull(it.index) }
        }.distinctUntilChanged().collect { report(it) }
    }
    DisposableEffect(Unit) { onDispose { report(emptyList()) } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.width(3.dp).height(18.dp).background(colors.accent))
            Text(title, color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LazyRow(state = list, modifier = focusModifier(firstMounted).testTag("$tagPrefix-row").onGloballyPositioned { bounds = it.boundsInRoot() }, horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 5.dp, horizontal = 3.dp)) {
            items(channels, key = { it.id }) { channel ->
                HomeCinematicChannelCard(channel, metrics, colors, labels, onFocus = { onFocus(channel, it) },
                    showChannelLogos = showLogos, tagPrefix = tagPrefix, program = program(channel), languageBadge = badge(channel),
                    modifier = if (firstFocus != null && channel.id == channels.firstOrNull()?.id) Modifier.focusRequester(firstFocus) else Modifier,
                    onClick = { onPlay(channel) })
            }
        }
    }
}