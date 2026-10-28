package com.yourapp.iptv.ui.component.channel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun ChannelLogo(name: String, url: String?, modifier: Modifier = Modifier, showImage: Boolean = true) {
    var failed by remember(url) { mutableStateOf(false) }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (!showImage || url.isNullOrBlank() || failed) {
            // This decorative mark fits the image slot; the full channel title scales separately.
            val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
            Text(name.trim().take(2).ifBlank { "?" }, style = MaterialTheme.typography.titleLarge,
                fontSize = 18.sp / scale, lineHeight = 22.sp / scale, maxLines = 1,
                color = MaterialTheme.colorScheme.primary)
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().testTag("channel-logo-image"),
                onError = { failed = true }
            )
        }
    }
}