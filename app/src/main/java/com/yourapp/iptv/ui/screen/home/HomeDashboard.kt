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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.HomeDashboardSelection
import com.yourapp.iptv.ui.component.channel.ChannelLogo
import com.yourapp.iptv.ui.theme.NeonBlue

private val dashboardBackground = Color(0xFF101E33)
private val mutedText = Color(0xFFB6CADC)

/** No network requests or player instances are created by the dashboard. */
@Composable
internal fun HomeDashboard(
    labels: HomeLabels,
    selection: HomeDashboardSelection,
    channelCount: Int,
    dashboardFocus: FocusRequester,
    onPlayChannel: (Channel) -> Unit,
    onLive: () -> Unit,
    onMovies: () -> Unit,
    onSeries: () -> Unit,
    onGuide: () -> Unit,
    onHistory: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val metrics = remember(maxWidth) { HomeDashboardLayout.forWidth(maxWidth.value.toInt()) }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(metrics.sectionSpacing.dp)
        ) {
            DashboardWelcome(labels, selection.highlighted, channelCount, metrics, onPlayChannel)
            DashboardSectionTitle(labels.text("اكتشف المحتوى", "Explore", "Entdecken"))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(end = 8.dp)
            ) {
                item(key = "live") {
                    DashboardAction(labels.section(HomeSection.LIVE), onLive,
                        metrics, HomeSection.LIVE, Modifier.focusRequester(dashboardFocus))
                }
                item(key = "movies") {
                    DashboardAction(labels.section(HomeSection.MOVIES), onMovies,
                        metrics, HomeSection.MOVIES)
                }
                item(key = "series") {
                    DashboardAction(labels.section(HomeSection.SERIES), onSeries,
                        metrics, HomeSection.SERIES)
                }
                item(key = "guide") {
                    DashboardAction(labels.section(HomeSection.GUIDE), onGuide,
                        metrics, HomeSection.GUIDE)
                }
                item(key = "history") {
                    DashboardAction(labels.section(HomeSection.HISTORY), onHistory,
                        metrics, HomeSection.HISTORY)
                }
            }
            if (selection.recent.isNotEmpty()) {
                DashboardChannelStrip(
                    labels.text("شوهدت مؤخرًا", "Recently watched", "Zuletzt gesehen"),
                    selection.recent, metrics, onPlayChannel
                )
            }
            if (selection.favorites.isNotEmpty()) {
                DashboardChannelStrip(
                    labels.section(HomeSection.FAVORITES), selection.favorites, metrics, onPlayChannel
                )
            }
            if (selection.recent.isEmpty() && selection.favorites.isEmpty()) {
                Text(
                    labels.text("شاهد قناة أو أضف قنوات للمفضلة لتظهر هنا.",
                        "Watch a channel or add favorites to see them here.",
                        "Sieh dir einen Sender an oder füge Favoriten hinzu."),
                    color = mutedText, style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DashboardWelcome(
    labels: HomeLabels,
    highlighted: Channel?,
    channelCount: Int,
    metrics: HomeDashboardMetrics,
    onPlayChannel: (Channel) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(), color = dashboardBackground,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, Color(0xFF33526E))
    ) {
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(Color(0xFF163D58), Color(0xFF10192E))))
                .padding(metrics.heroPadding.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("MN StreamX", style = MaterialTheme.typography.headlineMedium,
                color = NeonBlue, fontWeight = FontWeight.Bold)
            Text(
                labels.text("ترفيهك في مكان واحد", "Your entertainment, one place",
                    "Dein Entertainment an einem Ort"),
                style = if (metrics.compact) MaterialTheme.typography.titleSmall
                    else MaterialTheme.typography.titleMedium,
                color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Text(
                labels.text("$channelCount قناة متاحة", "$channelCount channels available",
                    "$channelCount Sender verfügbar"),
                style = MaterialTheme.typography.bodyMedium, color = mutedText
            )
            highlighted?.let { channel ->
                Spacer(Modifier.height(4.dp))
                DashboardAction(
                    labels.text("متابعة: ${channel.name}", "Continue: ${channel.name}",
                        "Weitersehen: ${channel.name}"),
                    { onPlayChannel(channel) }, metrics, HomeSection.LIVE,
                    modifier = Modifier.fillMaxWidth(), fullWidth = true
                )
            }
        }
    }
}

@Composable
private fun DashboardSectionTitle(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Spacer(Modifier.width(4.dp).height(22.dp).background(NeonBlue))
        Text(label, style = MaterialTheme.typography.titleLarge,
            color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DashboardChannelStrip(
    title: String,
    channels: List<Channel>,
    metrics: HomeDashboardMetrics,
    onPlay: (Channel) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DashboardSectionTitle(title)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(end = 8.dp)
        ) {
            items(channels, key = { it.id }) { channel ->
                DashboardChannelCard(channel, metrics, onPlay = { onPlay(channel) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DashboardChannelCard(channel: Channel, metrics: HomeDashboardMetrics, onPlay: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(focused) { if (focused) bringIntoView.bringIntoView() }
    Surface(
        onClick = onPlay,
        modifier = Modifier.width(metrics.channelWidth.dp).height(metrics.channelHeight.dp)
            .bringIntoViewRequester(bringIntoView)
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = channel.name },
        color = if (focused) Color(0xFF184C6A) else Color(0xFF14243B),
        border = BorderStroke(if (focused) 3.dp else 1.dp,
            if (focused) NeonBlue else Color(0xFF2D4660)),
        shadowElevation = if (focused) 8.dp else 0.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = MaterialTheme.shapes.small, color = Color(0xFF0B172B)) {
                Box(Modifier.size(metrics.logoSize.dp), contentAlignment = Alignment.Center) {
                    ChannelLogo(channel.name, channel.logoUrl, Modifier.fillMaxSize().padding(5.dp))
                }
            }
            Text(channel.name, Modifier.weight(1f), maxLines = 2,
                overflow = TextOverflow.Ellipsis, color = Color.White,
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DashboardAction(
    text: String,
    onClick: () -> Unit,
    metrics: HomeDashboardMetrics,
    section: HomeSection,
    modifier: Modifier = Modifier,
    fullWidth: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(focused) { if (focused) bringIntoView.bringIntoView() }
    val dimensions = if (fullWidth) modifier.fillMaxWidth().heightIn(min = metrics.actionHeight.dp)
        else modifier.width(metrics.actionWidth.dp).height(metrics.actionHeight.dp)
    Surface(
        onClick = onClick,
        modifier = dimensions.bringIntoViewRequester(bringIntoView)
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = text },
        color = if (focused) Color(0xFF1C5272) else Color(0xFF172B46),
        border = BorderStroke(if (focused) 3.dp else 1.dp,
            if (focused) NeonBlue else Color(0xFF2D4660)),
        shadowElevation = if (focused) 8.dp else 0.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HomeSidebarIcon(section, NeonBlue)
            Text(text, color = Color.White, maxLines = 2,
                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
        }
    }
}