package com.yourapp.iptv.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

/** Original Hero entry point; external callers need no changes. */
@Composable
internal fun HomeImageBackdrop(
    url: String?, colors: HomeTvPalette, modifier: Modifier = Modifier,
    large: Boolean = false, frame: File? = null
) {
    val source = remember(url) {
        HomeImageSource.remote(url)?.let(HomeArtworkSource::Logo) ?: HomeArtworkSource.Empty
    }
    HomeImageBackdrop(source, colors, modifier, large, frame)
}

/** Hero artwork is distinct from the lighter card backdrop and its bottom scrim. */
@Composable
internal fun HomeImageBackdrop(
    source: HomeArtworkSource, colors: HomeTvPalette,
    modifier: Modifier = Modifier, large: Boolean = false, frame: File? = null
) {
    val context = LocalContext.current
    var frameFailed by remember(frame?.absolutePath, frame?.lastModified()) { mutableStateOf(false) }
    val direction = LocalLayoutDirection.current
    val showingFrame = frame?.isFile == true && !frameFailed
    // Bright on the picture side, readable near the copy. Light theme uses white scrim.
    val scrimColor = if (colors.text == Color.White) Color.Black else Color.White
    val photoOverlay = listOf(scrimColor.copy(alpha = 0.12f),
        scrimColor.copy(alpha = 0.45f), scrimColor.copy(alpha = 0.87f))
    val baseOverlay = if (showingFrame) photoOverlay else colors.heroOverlay
    val overlay = if (showingFrame) {
        if (direction == LayoutDirection.Rtl) baseOverlay else baseOverlay.reversed()
    } else if (direction == LayoutDirection.Rtl) baseOverlay.reversed() else baseOverlay
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
                // Logos are square artwork, never cinematic frames. Keep them
                // contained on the image side instead of filling the whole Hero.
                modifier = Modifier.align(Alignment.CenterEnd)
                    .fillMaxHeight(0.72f).aspectRatio(1f).padding(8.dp)
            )
        }
        if (source is HomeArtworkSource.Monogram) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(source.text, color = colors.accent,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold)
            }
        }
        // A captured frame fills the entire Hero. Logo remains the fallback underneath it.
        if (showingFrame && frame != null) {
            val capturedRequest = remember(context, frame.absolutePath, frame.lastModified()) {
                ImageRequest.Builder(context).data(frame).size(960, 540)
                    .memoryCacheKey("hero:${frame.absolutePath}:${frame.lastModified()}")
                    .crossfade(200).build()
            }
            AsyncImage(model = capturedRequest, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize(),
                onError = { frameFailed = true })
        }
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(overlay)))
    }
}