package com.yourapp.iptv.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import coil.compose.AsyncImage
import coil.request.ImageRequest

/** Original Hero entry point; external callers need no changes. */
@Composable
internal fun HomeImageBackdrop(
    url: String?, colors: HomeTvPalette, modifier: Modifier = Modifier,
    large: Boolean = false
) {
    val source = remember(url) {
        HomeImageSource.remote(url)?.let(HomeArtworkSource::Logo) ?: HomeArtworkSource.Empty
    }
    HomeImageBackdrop(source, colors, modifier, large)
}

/** Hero artwork is distinct from the lighter card backdrop and its bottom scrim. */
@Composable
internal fun HomeImageBackdrop(
    source: HomeArtworkSource, colors: HomeTvPalette,
    modifier: Modifier = Modifier, large: Boolean = false
) {
    val context = LocalContext.current
    val direction = LocalLayoutDirection.current
    val overlay = if (direction == LayoutDirection.Rtl) {
        colors.heroOverlay.reversed()
    } else colors.heroOverlay
    val request = remember(context, source, large) {
        (source as? HomeArtworkSource.Logo)?.let { logo ->
            ImageRequest.Builder(context).data(logo.url)
                .size(if (large) 720 else 360, if (large) 360 else 180)
                .crossfade(false).build()
        }
    }
    Box(modifier.background(Brush.horizontalGradient(colors.heroGradient))) {
        if (request != null) {
            AsyncImage(
                model = request, contentDescription = null,
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterEnd,
                alpha = if (large) 0.95f else 0.85f,
                modifier = Modifier.matchParentSize()
            )
        }
        if (source is HomeArtworkSource.Monogram) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(source.text, color = colors.accent,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold)
            }
        }
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(overlay)))
    }
}