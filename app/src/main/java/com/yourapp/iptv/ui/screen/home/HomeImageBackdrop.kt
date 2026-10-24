package com.yourapp.iptv.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import coil.compose.AsyncImage
import coil.request.ImageRequest

/** Uses only channel logos already supplied by the playlist; never requests VOD metadata. */
@Composable
internal fun HomeImageBackdrop(
    url: String?, colors: HomeTvPalette, modifier: Modifier = Modifier,
    large: Boolean = false
) {
    val context = LocalContext.current
    val safeUrl = remember(url) { HomeImageSource.remote(url) }
    val direction = LocalLayoutDirection.current
    val overlay = if (direction == LayoutDirection.Rtl)
        colors.heroOverlay.reversed() else colors.heroOverlay
    val request = remember(context, safeUrl, large) {
        safeUrl?.let {
            ImageRequest.Builder(context).data(it)
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
        // Contrast-safe scrim, including when a logo has its own bright background.
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(overlay)))
    }
}