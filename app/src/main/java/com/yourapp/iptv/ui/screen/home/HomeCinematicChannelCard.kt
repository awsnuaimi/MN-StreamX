package com.yourapp.iptv.ui.screen.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.ui.component.channel.ChannelLogo
import com.yourapp.iptv.ui.state.HomeProgramUiState
import com.yourapp.iptv.ui.component.common.tvFocusIndicator

/** Separate artwork and programme caption, with one stable focus target. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeCinematicChannelCard(channel: Channel, metrics: HomeDashboardMetrics, colors: HomeTvPalette,
    labels: HomeLabels, onFocus: (Boolean) -> Unit = {}, showChannelLogos: Boolean = true,
    program: HomeProgramUiState = HomeProgramUiState(), languageBadge: String? = null,
    tagPrefix: String = "home-channel", modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bring = remember { BringIntoViewRequester() }
    LaunchedEffect(focused) { if (focused) bring.bringIntoView() }
    val frame = rememberChannelFrame(channel)
    val current = program.takeIf { it.matches(channel) }?.let { it.schedule.current(it.nowMillis) }
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    Surface(onClick = onClick, modifier = modifier.tvFocusIndicator(RoundedCornerShape(11.dp)).width(metrics.channelWidth.dp)
        .heightIn(min = (metrics.channelWidth * 0.65f * scale).dp)
        .bringIntoViewRequester(bring).onFocusChanged { focused = it.isFocused; onFocus(it.isFocused) }
        .testTag("$tagPrefix-${channel.id}"), shape = RoundedCornerShape(11.dp), color = if (focused) colors.focused else colors.surface,
        border = BorderStroke(if (focused) 2.dp else 1.dp, if (focused) colors.accent else colors.outline)) {
        Column {
            Box(Modifier.fillMaxWidth().height(64.dp).background(Brush.verticalGradient(colors.cardFallbackGradient))) {
                if (showChannelLogos) Box(Modifier.align(Alignment.Center).size(42.dp).testTag("channel-card-logo")) {
                    ChannelLogo(channel.name, channel.logoUrl, Modifier.fillMaxSize())
                }
                SavedChannelFrame(frame, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                Surface(Modifier.align(Alignment.TopStart).padding(8.dp), color = colors.liveBadge, shape = RoundedCornerShape(4.dp)) {
                    Text("LIVE", Modifier.padding(horizontal = 5.dp, vertical = 2.dp), color = Color.White,
                        fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                languageBadge?.let {
                    Surface(Modifier.align(Alignment.TopEnd).padding(8.dp), color = colors.selected, shape = RoundedCornerShape(4.dp)) {
                        Text(it, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = colors.text, fontSize = 9.sp)
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(10.dp).testTag("channel-card-caption"),
                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(channel.name, Modifier.weight(1f), color = colors.text, fontSize = 13.sp,
                        lineHeight = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (channel.isFavorite) Text("★", color = colors.accent, fontSize = 12.sp)
                }
                Text(current?.title ?: labels.text("بث مباشر", "Live TV", "Live-TV"), color = colors.muted,
                    fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (current != null) LinearProgressIndicator(progress = { program.schedule.progress(program.nowMillis) },
                    modifier = Modifier.fillMaxWidth().height(3.dp).padding(top = 1.dp), color = colors.accent, trackColor = colors.outline)
            }
        }
    }
}