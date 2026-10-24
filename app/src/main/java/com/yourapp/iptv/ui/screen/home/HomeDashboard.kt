package com.yourapp.iptv.ui.screen.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.HomeDashboardSelection
import com.yourapp.iptv.ui.component.channel.ChannelLogo
import com.yourapp.iptv.ui.theme.NeonBlue

/** Focus-first launcher: only bounded, existing catalog data appears on the screen. */
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Surface(color = Color(0xFF112845), shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, Color(0xFF254663))) {
            Column(Modifier.fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(Color(0xFF173A58), Color(0xFF11203B))))
                .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("MN StreamX", style = MaterialTheme.typography.headlineMedium,
                    color = NeonBlue)
                Text(labels.text("كل ما تحب مشاهدته في مكان واحد",
                    "Your entertainment, one place", "Dein Entertainment an einem Ort"),
                    style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(labels.text("$channelCount قناة متاحة", "$channelCount channels available",
                    "$channelCount Sender verfügbar"), style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB6CADC))
                selection.highlighted?.let { channel ->
                    DashboardAction(labels.text("تشغيل: ${channel.name}", "Play: ${channel.name}",
                        "Abspielen: ${channel.name}"), onClick = { onPlayChannel(channel) },
                        modifier = Modifier.fillMaxWidth(), section = HomeSection.LIVE)
                }
            }
        }
        Text(labels.text("اكتشف المحتوى", "Explore", "Entdecken"),
            style = MaterialTheme.typography.titleLarge, color = Color.White)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(end = 8.dp)) {
            item(key = "live") { DashboardAction(labels.section(HomeSection.LIVE), onLive,
                modifier = Modifier.focusRequester(dashboardFocus), section = HomeSection.LIVE) }
            item(key = "movies") { DashboardAction(labels.section(HomeSection.MOVIES), onMovies, section = HomeSection.MOVIES) }
            item(key = "series") { DashboardAction(labels.section(HomeSection.SERIES), onSeries, section = HomeSection.SERIES) }
            item(key = "guide") { DashboardAction(labels.section(HomeSection.GUIDE), onGuide, section = HomeSection.GUIDE) }
            item(key = "history") { DashboardAction(labels.section(HomeSection.HISTORY), onHistory, section = HomeSection.HISTORY) }
        }
        if (selection.recent.isNotEmpty()) {
            DashboardChannelStrip(labels.text("شوهدت مؤخرًا", "Recently watched", "Zuletzt gesehen"),
                selection.recent, onPlayChannel)
        }
        if (selection.favorites.isNotEmpty()) {
            DashboardChannelStrip(labels.section(HomeSection.FAVORITES), selection.favorites, onPlayChannel)
        }
        if (selection.recent.isEmpty() && selection.favorites.isEmpty()) {
            Text(labels.text("شاهد قناة أو أضف قنوات إلى المفضلة لتظهر هنا.",
                "Watch a channel or add favorites to see them here.",
                "Sieh dir einen Sender an oder füge Favoriten hinzu."),
                color = Color(0xFFB6CADC), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DashboardChannelStrip(title: String, channels: List<Channel>, onPlay: (Channel) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(end = 8.dp)) {
            items(channels, key = { it.id }) { channel ->
                DashboardChannelCard(channel, onPlay = { onPlay(channel) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DashboardChannelCard(channel: Channel, onPlay: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(focused) { if (focused) bringIntoView.bringIntoView() }
    Surface(onClick = onPlay,
        modifier = Modifier.width(208.dp).heightIn(min = 100.dp)
            .bringIntoViewRequester(bringIntoView)
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = channel.name },
        color = if (focused) Color(0xFF174968) else Color(0xFF14243B),
        border = BorderStroke(if (focused) 3.dp else 1.dp,
            if (focused) NeonBlue else Color(0xFF2D4660)),
        shadowElevation = if (focused) 7.dp else 0.dp,
        shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChannelLogo(channel.name, channel.logoUrl, Modifier.size(52.dp))
            Text(channel.name, Modifier.weight(1f), maxLines = 2,
                overflow = TextOverflow.Ellipsis, color = Color.White,
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DashboardAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    section: HomeSection) {
    var focused by remember { mutableStateOf(false) }
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(focused) { if (focused) bringIntoView.bringIntoView() }
    Surface(onClick = onClick,
        modifier = modifier.widthIn(min = 148.dp).heightIn(min = 62.dp)
            .bringIntoViewRequester(bringIntoView)
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = text },
        color = if (focused) Color(0xFF1C4D6D) else Color(0xFF172B46),
        border = BorderStroke(if (focused) 3.dp else 1.dp,
            if (focused) NeonBlue else Color(0xFF2D4660)),
        shadowElevation = if (focused) 7.dp else 0.dp,
        shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeSidebarIcon(section, NeonBlue)
            Text(text, color = Color.White, maxLines = 2,
                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
        }
    }
}