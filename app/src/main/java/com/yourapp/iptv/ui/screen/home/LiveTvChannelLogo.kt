package com.yourapp.iptv.ui.screen.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.yourapp.iptv.ui.presentation.LiveChannelRow

/** Visible rows use the shared Coil cache. No player or image fetch is started for offscreen rows. */
@Composable
internal fun LiveTvChannelLogo(row: LiveChannelRow, preferred: String?, colors: HomeTvPalette,
    showLogos: Boolean = true) {
    val preferredChannel = row.channel(preferred)
    val urls = remember(row.variants, preferredChannel, showLogos) {
        if (!showLogos) emptyList() else (listOf(preferredChannel) + row.variants)
            .mapNotNull { it.logoUrl?.trim()?.takeIf(String::isNotBlank) }.distinct().take(4)
    }
    var candidate by remember(urls) { mutableIntStateOf(0) }
    var loaded by remember(urls, candidate) { mutableStateOf(false) }
    val url = urls.getOrNull(candidate)
    val context = LocalContext.current
    val request = remember(context, url) {
        url?.let { ImageRequest.Builder(context).data(it).size(256, 128).crossfade(false).build() }
    }
    Box(Modifier.width(48.dp).height(32.dp).clip(RoundedCornerShape(6.dp))
        .background(colors.logoSurface).border(0.5.dp, colors.outline, RoundedCornerShape(6.dp))
        .testTag("live-logo-${row.key}"), contentAlignment = Alignment.Center) {
        if (!loaded) Canvas(Modifier.size(22.dp).testTag("live-logo-fallback-${row.key}")) {
            val stroke = Stroke(1.5.dp.toPx())
            drawRoundRect(colors.muted, Offset(1.dp.toPx(), 3.dp.toPx()),
                Size(size.width - 2.dp.toPx(), size.height - 8.dp.toPx()), CornerRadius(2.dp.toPx()), style = stroke)
            drawLine(colors.muted, Offset(size.width * .3f, size.height - 2.dp.toPx()),
                Offset(size.width * .7f, size.height - 2.dp.toPx()), 1.5.dp.toPx())
        }
        if (request != null) key(url) {
            AsyncImage(request, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(2.dp)
                    .testTag(if (loaded) "live-logo-loaded-${row.key}" else "live-logo-loading-${row.key}"),
                onSuccess = { loaded = true }, onError = { candidate++ })
        }
    }
}