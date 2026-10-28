package com.yourapp.iptv.ui.screen.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.ui.component.channel.ChannelLogo

/** Smart Home card. Does not request a stream or create a player. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeCinematicChannelCard(
    channel: Channel,
    metrics: HomeDashboardMetrics,
    colors: HomeTvPalette,
    labels: HomeLabels,
    onFocus: (Boolean) -> Unit = {},
    showChannelLogos: Boolean = true,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(focused) {
        if (focused) bringIntoView.bringIntoView()
    }
    val artwork = remember(channel.id, channel.name, channel.logoUrl, showChannelLogos) {
        if (showChannelLogos) HomeArtworkSource.of(channel) else HomeArtworkSource.fallback(channel.name)
    }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = tween(150), label = "channelFocusScale"
    )
    val group = channel.group.orEmpty()
    val frame = rememberChannelFrame(channel)
    val description = if (group.isBlank()) {
        labels.text("قناة ${channel.name}، بث مباشر",
            "${channel.name}, live TV", "${channel.name}, Live-TV")
    } else {
        labels.text("قناة ${channel.name}، بث مباشر، $group",
            "${channel.name}, live TV, $group",
            "${channel.name}, Live-TV, $group")
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.width(metrics.channelWidth.dp)
            .aspectRatio(metrics.channelAspectRatio)
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .bringIntoViewRequester(bringIntoView)
            .onFocusChanged { focused = it.isFocused; onFocus(it.isFocused) }
            .testTag("home-channel-${channel.id}")
            .semantics { contentDescription = description },
        color = colors.surface,
        border = BorderStroke(if (focused) 3.dp else 1.dp,
            if (focused) colors.accent else colors.outline),
        shadowElevation = if (focused) 8.dp else 0.dp,
        shape = RoundedCornerShape(14.dp)
    ) {
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(colors.cardFallbackGradient))) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.58f).align(Alignment.TopCenter)) {
                ChannelArtworkLayer(artwork, channel.name, metrics, colors)
            }
            SavedChannelFrame(frame, Modifier.matchParentSize())
            // Only the text area receives a dark scrim; the logo stays legible.
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.65f)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(colors.cardOverlay)))
            Surface(
                modifier = Modifier.align(AbsoluteAlignment.TopLeft).padding(8.dp),
                color = colors.liveBadge,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("LIVE", Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    color = Color.White, style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold)
            }
            if (channel.isFavorite) {
                Text("★", Modifier.align(AbsoluteAlignment.TopRight).padding(8.dp),
                    color = colors.accent, style = MaterialTheme.typography.titleMedium)
            }
            Row(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(10.dp).testTag("channel-card-caption"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(shape = RoundedCornerShape(6.dp), color = colors.logoSurface,
                    modifier = Modifier.size(30.dp)) {
                    ChannelLogo(channel.name, channel.logoUrl,
                        Modifier.fillMaxSize().padding(3.dp), showImage = showChannelLogos)
                }
                Column(Modifier.weight(1f)) {
                    Text(channel.name, color = Color.White, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold)
                    if (group.isNotBlank()) {
                        Text(group, color = Color(0xFFE1E8F0), maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelArtworkLayer(
    artwork: HomeArtworkSource,
    channelName: String,
    metrics: HomeDashboardMetrics,
    colors: HomeTvPalette
) {
    // Loading/Empty show the gradient. A failed logo displays a monogram.
    var failed by remember(artwork, channelName) { mutableStateOf(false) }
    val visible = if (failed) HomeArtworkSource.fallback(channelName) else artwork
    when (visible) {
        is HomeArtworkSource.Logo -> {
            val context = LocalContext.current
            val targetPx = metrics.logoSize * 3
            val request = remember(context, visible.url, targetPx) {
                ImageRequest.Builder(context).data(visible.url)
                    .size(targetPx, targetPx).crossfade(200).build()
            }
            AsyncImage(
                model = request, contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)
                    .testTag("channel-card-logo"),
                onError = { failed = true }
            )
        }
        is HomeArtworkSource.Monogram -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(visible.text, color = colors.accent,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold)
            }
        }
        HomeArtworkSource.Empty -> Unit
    }
}