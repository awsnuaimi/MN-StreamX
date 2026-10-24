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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yourapp.iptv.ui.component.player.ChannelFrameStore
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.HomeDashboardSelection

/** Smart Home only projects cached channel state. It never creates a player. */
@Composable
internal fun HomeDashboard(
    labels: HomeLabels,
    selection: HomeDashboardSelection,
    channelCount: Int,
    dashboardFocus: FocusRequester,
    isDarkTheme: Boolean,
    isNightMode: Boolean,
    onPlayChannel: (Channel) -> Unit,
    onLive: () -> Unit,
    onMovies: () -> Unit,
    onSeries: () -> Unit,
    onGuide: () -> Unit,
    onHistory: () -> Unit
) {
    val colors = HomeTvPalettes.forMode(isDarkTheme, isNightMode)
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background)) {
        val metrics = remember(maxWidth) { HomeDashboardLayout.forWidth(maxWidth.value.toInt()) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(metrics.sectionSpacing.dp)) {
            DashboardWelcome(labels, selection.highlighted, channelCount, metrics, colors, onPlayChannel)
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
            if (selection.recent.isNotEmpty()) DashboardChannelStrip(
                labels.text("شوهدت مؤخرًا", "Recently watched", "Zuletzt gesehen"),
                selection.recent, metrics, colors, labels, onPlayChannel)
            if (selection.favorites.isNotEmpty()) DashboardChannelStrip(
                labels.section(HomeSection.FAVORITES), selection.favorites, metrics, colors, labels, onPlayChannel)
            if (selection.recent.isEmpty() && selection.favorites.isEmpty()) Text(
                labels.text("شاهد قناة أو أضف قنوات للمفضلة لتظهر هنا.",
                    "Watch a channel or add favorites to see them here.",
                    "Sieh dir einen Sender an oder füge Favoriten hinzu."),
                color = colors.muted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DashboardWelcome(labels: HomeLabels, highlighted: Channel?, channelCount: Int,
    metrics: HomeDashboardMetrics, colors: HomeTvPalette, onPlayChannel: (Channel) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var resumeEpoch by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeEpoch++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val savedFrame = remember(context, highlighted?.id, highlighted?.streamUrl, resumeEpoch) {
        highlighted?.let { ChannelFrameStore.fileFor(context, it) }
    }
    Surface(Modifier.fillMaxWidth(), color = colors.surface,
        shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, colors.outline)) {
        Box(Modifier.fillMaxWidth()) {
            HomeImageBackdrop(highlighted?.logoUrl, colors,
                Modifier.matchParentSize(), large = true, frame = savedFrame)
            Column(Modifier.fillMaxWidth().padding(metrics.heroPadding.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("MN StreamX", style = MaterialTheme.typography.headlineMedium,
                    color = colors.accent, fontWeight = FontWeight.Bold)
                Text(labels.text("ترفيهك في مكان واحد", "Your entertainment, one place",
                    "Dein Entertainment an einem Ort"),
                    style = if (metrics.compact) MaterialTheme.typography.titleSmall
                        else MaterialTheme.typography.titleMedium,
                    color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(labels.text("$channelCount قناة متاحة", "$channelCount channels available",
                    "$channelCount Sender verfügbar"),
                    style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                highlighted?.let { channel ->
                    Spacer(Modifier.height(4.dp))
                    DashboardAction(labels.text("متابعة: ${channel.name}", "Continue: ${channel.name}",
                        "Weitersehen: ${channel.name}"), { onPlayChannel(channel) }, metrics,
                        HomeSection.LIVE, colors, Modifier.fillMaxWidth(), fullWidth = true)
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
    onPlay: (Channel) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DashboardSectionTitle(title, colors)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
            items(channels, key = { it.id }) { channel ->
                HomeCinematicChannelCard(channel, metrics, colors, labels,
                    onClick = { onPlay(channel) })
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
        .heightIn(min = metrics.actionHeight.dp)
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