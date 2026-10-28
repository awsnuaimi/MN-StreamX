package com.yourapp.iptv.ui.screen.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.HomeDashboardSelection
import com.yourapp.iptv.ui.state.HomeProgramUiState
import kotlinx.coroutines.delay

/** Smart Home only projects cached channel state. It never creates a player. */
@Composable
internal fun HomeDashboard(
    labels: HomeLabels,
    selection: HomeDashboardSelection,
    channelCount: Int,
    dashboardFocus: FocusRequester,
    suggestedChannels: List<Channel>,
    isDarkTheme: Boolean,
    isNightMode: Boolean,
    onPlayChannel: (Channel) -> Unit,
    onLive: () -> Unit,
    onMovies: () -> Unit,
    onSeries: () -> Unit,
    onGuide: () -> Unit,
    onHistory: () -> Unit,
    programState: HomeProgramUiState = HomeProgramUiState(),
    onPreviewChannel: (Channel?) -> Unit = {},
    theme: com.yourapp.iptv.domain.model.HomeTheme = if (isDarkTheme)
        com.yourapp.iptv.domain.model.HomeTheme.DARK_BLUE else com.yourapp.iptv.domain.model.HomeTheme.LIGHT,
    showChannelLogos: Boolean = true
) {
    val colors = HomeTvPalettes.forTheme(theme, isNightMode)
    val featured = remember(selection, suggestedChannels) {
        HomeFeaturedChannels.select(selection, suggestedChannels)
    }
    val candidates = remember(selection, suggestedChannels) {
        (selection.recent + selection.favorites + suggestedChannels).distinctBy { it.id to it.streamUrl }
    }
    var focusedChannel by remember { mutableStateOf<Channel?>(null) }
    var previewKey by remember { mutableStateOf<Pair<String, String>?>(null) }
    val highlighted = candidates.firstOrNull { (it.id to it.streamUrl) == previewKey }
        ?: selection.highlighted ?: featured.channels.firstOrNull()
    LaunchedEffect(focusedChannel) {
        val channel = focusedChannel ?: return@LaunchedEffect
        delay(280)
        previewKey = channel.id to channel.streamUrl
    }
    LaunchedEffect(highlighted) { onPreviewChannel(highlighted) }
    val onChannelFocus: (Channel, Boolean) -> Unit = { channel, focused ->
        if (focused) focusedChannel = channel
        else if (focusedChannel?.id == channel.id && focusedChannel?.streamUrl == channel.streamUrl) {
            focusedChannel = null
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background)) {
        val metrics = remember(maxWidth) { HomeDashboardLayout.forWidth(maxWidth.value.toInt()) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(metrics.sectionSpacing.dp)) {
            if (featured.channels.isNotEmpty()) {
                val featuredTitle = when (featured.kind) {
                    HomeFeaturedKind.RECENT -> labels.text(
                        "شوهدت مؤخرًا", "Recently watched", "Zuletzt gesehen")
                    HomeFeaturedKind.FAVORITES -> labels.section(HomeSection.FAVORITES)
                    HomeFeaturedKind.DISCOVER -> labels.text(
                        "اكتشف قنوات مباشرة", "Discover live channels", "Live-Sender entdecken")
                }
                DashboardChannelStrip(featuredTitle, featured.channels,
                    metrics, colors, labels, onChannelFocus, onPlayChannel, showChannelLogos)
            }
            DashboardWelcome(labels, highlighted, channelCount,
                metrics, colors, programState, showChannelLogos, onPlayChannel)
            DashboardSectionTitle(labels.text("اكتشف المحتوى", "Explore", "Entdecken"), colors)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
                item(key = "live") { DashboardAction(labels.section(HomeSection.LIVE), onLive,
                    metrics, HomeSection.LIVE, colors, Modifier.focusRequester(dashboardFocus)) }
                item(key = "movies") { DashboardAction(labels.section(HomeSection.MOVIES), onMovies,
                    metrics, HomeSection.MOVIES, colors) }
                item(key = "series") { DashboardAction(labels.section(HomeSection.SERIES), onSeries,
                    metrics, HomeSection.SERIES, colors) }
                item(key = "guide") { DashboardAction(labels.section(HomeSection.GUIDE), onGuide,
                    metrics, HomeSection.GUIDE, colors) }
                item(key = "history") { DashboardAction(labels.section(HomeSection.HISTORY), onHistory,
                    metrics, HomeSection.HISTORY, colors) }
            }
            if (featured.kind == HomeFeaturedKind.RECENT && selection.favorites.isNotEmpty()) {
                val remainingFavorites = selection.favorites.filterNot { favorite ->
                    featured.channels.any { it.id == favorite.id }
                }
                if (remainingFavorites.isNotEmpty()) {
                    DashboardChannelStrip(labels.section(HomeSection.FAVORITES),
                        remainingFavorites, metrics, colors, labels, onChannelFocus, onPlayChannel, showChannelLogos)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun DashboardWelcome(labels: HomeLabels, highlighted: Channel?, channelCount: Int,
    metrics: HomeDashboardMetrics, colors: HomeTvPalette,
    programState: HomeProgramUiState = HomeProgramUiState(), showChannelLogos: Boolean = true,
    onPlayChannel: (Channel) -> Unit) {
    val savedFrame = rememberChannelFrame(highlighted)
    Surface(Modifier.fillMaxWidth(), color = colors.surface,
        shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, colors.outline)) {
        Box(Modifier.fillMaxWidth()) {
            HomeImageBackdrop(highlighted?.let {
                if (showChannelLogos) HomeArtworkSource.of(it) else HomeArtworkSource.fallback(it.name)
            } ?: HomeArtworkSource.Monogram("MN"), colors,
                Modifier.matchParentSize(), large = true, frame = savedFrame)
            Column(Modifier.fillMaxWidth(0.52f).padding(minOf(metrics.heroPadding, 14).dp)
                .testTag("hero-copy"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("MN StreamX", style = MaterialTheme.typography.titleSmall,
                    color = colors.accent, fontWeight = FontWeight.Bold)
                Text(highlighted?.name ?: labels.text("ترفيهك في مكان واحد", "Your entertainment, one place",
                    "Dein Entertainment an einem Ort"),
                    modifier = Modifier.testTag("hero-channel-name"),
                    style = MaterialTheme.typography.titleLarge, color = colors.text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(labels.text("$channelCount قناة متاحة", "$channelCount channels available",
                    "$channelCount Sender verfügbar"),
                    style = MaterialTheme.typography.labelSmall, color = colors.muted)
                Box(Modifier.fillMaxWidth().height(44.dp)) {
                    highlighted?.let { HomeNowPlaying(it, programState, labels, colors) }
                }
                highlighted?.let { channel ->
                    DashboardAction(labels.text("مشاهدة القناة", "Watch channel",
                        "Sender ansehen"), { onPlayChannel(channel) }, metrics,
                        HomeSection.LIVE, colors, Modifier.fillMaxWidth().testTag("hero-continue"), fullWidth = true)
                }
            }
        }
    }
}

@Composable
private fun DashboardSectionTitle(label: String, colors: HomeTvPalette) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Spacer(Modifier.width(4.dp).height(22.dp).background(colors.accent))
        Text(label, style = MaterialTheme.typography.titleLarge, color = colors.text,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DashboardChannelStrip(title: String, channels: List<Channel>,
    metrics: HomeDashboardMetrics, colors: HomeTvPalette, labels: HomeLabels,
    onFocus: (Channel, Boolean) -> Unit, onPlay: (Channel) -> Unit, showChannelLogos: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DashboardSectionTitle(title, colors)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
            items(channels, key = { it.id }) { channel ->
                HomeCinematicChannelCard(channel, metrics, colors, labels,
                    onFocus = { onFocus(channel, it) }, showChannelLogos = showChannelLogos, onClick = { onPlay(channel) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DashboardAction(text: String, onClick: () -> Unit,
    metrics: HomeDashboardMetrics, section: HomeSection, colors: HomeTvPalette,
    modifier: Modifier = Modifier, fullWidth: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(focused) { if (focused) bringIntoView.bringIntoView() }
    val dimensions = if (fullWidth) modifier.fillMaxWidth()
        .height(minOf(metrics.actionHeight, 64).dp)
        else modifier.width(metrics.actionWidth.dp).height(metrics.actionHeight.dp)
    Surface(onClick = onClick,
        modifier = dimensions.bringIntoViewRequester(bringIntoView)
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = text },
        color = if (focused) colors.focused else colors.surface,
        border = BorderStroke(if (focused) 3.dp else 1.dp,
            if (focused) colors.accent else colors.outline),
        shadowElevation = if (focused) 8.dp else 0.dp,
        shape = MaterialTheme.shapes.medium) {
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            if (focused) listOf(colors.focused, colors.selected)
            else colors.heroGradient))) {
            Row(Modifier.fillMaxSize().padding(horizontal = 13.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeSidebarIcon(section, colors.accent)
                Text(text, color = colors.text, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}